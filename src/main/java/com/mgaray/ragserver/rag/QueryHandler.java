package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Embedder;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class QueryHandler {

    private final WebappConfig webappConfig;
    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;

    public QueryHandler(WebappConfig webappConfig,
                        IDatastore datastore,
                        IVectorStore<Chunk> vectorStore,
                        String sourceManifestId) {
        this.webappConfig = webappConfig;
        this.datastore = datastore;
        this.vectorStore = vectorStore;
        String sourceManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(sourceManifestLocation, IngestionManifest.class);
        this.embeddingModel = Embedder.createEmbeddingModel(ingestionManifest.runDefinition().embeddingSpec(), webappConfig.openApiKey());
        this.chatModel = createChatModel(webappConfig);
    }

    public static ChatModel createChatModel(WebappConfig config) {
        String chatModelName = switch(config.chatModelType()) {
            case OPEN_AI_GPT_4O_MINI -> "gpt-4o-mini";
            case OPEN_AI_GPT_4O -> "gpt-4o";
            case OPEN_AI_GPT_56_SOL -> "gpt-5.6-sol";
        };
        return OpenAiChatModel.builder()
                .apiKey(config.openApiKey())
                .modelName(chatModelName)
                //.temperature(0.0)         // 0.0 = deterministic output , "gpt-5.6-sol" does not support temperature!=1
                .build();
    }

    public Response query(Request request) {
        String userPrompt = request.userPrompt();
        //userPrompt = chatModel.chat("could you please expand on this prompt in the content of portland city codes: " + userPrompt);
        SessionState sessionState = getSessionState(request);
        String vectorStoreQuery = createVectorStoreQuery(sessionState, userPrompt);
        float[] queryVector = embeddingModel.embed(vectorStoreQuery).content().vector();
        List<VectorMatch<Chunk>> vectorMatches = vectorStore.get(queryVector, webappConfig.chunksToProvide());
        Map<String, VectorMatch<Chunk>> lookup = new HashMap<>();
        List<DataSource> dataSources = lossyTransform(vectorMatches, lookup);
        String prompt = createPrompt(dataSources, sessionState.promptExchanges(), userPrompt);
        System.out.println("prompt: " + prompt);
        String chatModelResponseJson = chatModel.chat(prompt);
        System.out.println("chatModelResponseJson: " + chatModelResponseJson);
        ChatModelResponse chatModelResponse = JsonUtils.toObject(chatModelResponseJson, ChatModelResponse.class); //todo exception handling
        List<String> sources = sources(chatModelResponse, lookup);
        String chatResponse = chatModelResponse.response();
        sessionState.promptExchanges().add(new PromptExchange(userPrompt, chatResponse, chatModelResponse.dataSourcesUsed()));
        String sessionStateJson = JsonUtils.toJson(sessionState);
        return new Response(chatResponse, sources, sessionStateJson, prompt + "\n\n" + chatModelResponseJson);
    }

    private SessionState getSessionState(Request request) {
        String sessionStateJson = request.sessionState();
        if (sessionStateJson == null) {
            return new SessionState(new ArrayList<>());
        }
        SessionState sessionState = JsonUtils.toObject(request.sessionState(), SessionState.class);
        if (sessionState.promptExchanges() == null) {
            return new SessionState(new ArrayList<>());
        }
        return sessionState;
    }

    private String createVectorStoreQuery(SessionState sessionState, String userPrompt) {
        StringBuilder stringBuilder = new StringBuilder();
        for (PromptExchange promptExchange : sessionState.promptExchanges()) {
            stringBuilder.append(promptExchange.prompt() + "\n");
            stringBuilder.append(promptExchange.response() + "\n");
        }
        stringBuilder.append(userPrompt);
        return stringBuilder.toString();
    }

    private List<DataSource> lossyTransform(List<VectorMatch<Chunk>> vectorMatches, Map<String, VectorMatch<Chunk>> lookup) {
        List<DataSource> dataSources = new ArrayList<>();
        for (VectorMatch<Chunk> vectorMatch : vectorMatches) {
            Chunk chunk = vectorMatch.record();
            String id = UUID.randomUUID().toString();  //prevents hallucination but is hard to debug todo
            String chunkText = datastore.readString(chunk.textLocation());
            dataSources.add(new DataSource(id, chunkText));
            lookup.put(id, vectorMatch);
        }
        return dataSources;
    }

    private List<String> sources(ChatModelResponse chatModelResponse, Map<String, VectorMatch<Chunk>> lookup) {
        Set<String> sources = new HashSet<>();
        for (String dataSourceKey : chatModelResponse.dataSourcesUsed()) {
            VectorMatch<Chunk> vectorMatch = lookup.get(dataSourceKey);  //may be null on hallucination todo
            sources.add(vectorMatch.record().sourceRecord().sourceUrl());
        }
        return new ArrayList<>(sources);
    }

    private String createPrompt(List<DataSource> dataSources, List<PromptExchange> promptExchanges, String userPrompt) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(promptPrefix);
        prompt.append("DATA SOURCES:\n");
        for (DataSource dataSource : dataSources) {
            prompt.append(JsonUtils.toJson(dataSource) + "\n");
        }
        for (PromptExchange promptExchange : promptExchanges) {
            prompt.append("\nPROMPT:\n");
            prompt.append("     " + promptExchange.prompt);
            prompt.append("\nRESPONSE:\n");
            prompt.append("     " + promptExchange.response);
        }
        prompt.append("\nPROMPT:\n");
        prompt.append("     " + userPrompt);
        return prompt.toString();
    }

    private final String promptPrefix = """
Use the following data sources only to continue the conversation.
If the source data does not include data to answer the prompt, say so.
Include the ids of the data sources you used to form your response.
Always respond in the following json format, without a prefix or suffix:
{ "dataSourcesUsed": ["<id1>","<id2>","<id3>",...], "response": "<next response>" }

""";

//    private final String promptContinued = """
//
//DATA SOURCES:
//{"id" : "32123", "text : "data chunk of text" }
//{"id" : "32123", "text" : "data chunk of text" }
//{"id" : "32123", "text" : "data chunk of text" }
//
//PROMPT:
//RESPONSE:
//PROMPT:
//            """;

    private record ChatModelResponse(List<String> dataSourcesUsed, String response) {}

    private record SessionState(List<PromptExchange> promptExchanges) {}

    private record PromptExchange(String prompt, String response, List<String> dataSourceIds) {}

    private record DataSource(String id, String text) {}

}
