package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Embedder;
import com.mgaray.ragserver.common.EncryptionDelegate;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class QueryHandler {

    private static final boolean encryptSessionState = false;

    private final WebappConfig webappConfig;
    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final EncryptionDelegate encryptionDelegate;

    public QueryHandler(WebappConfig webappConfig,
                        IDatastore datastore,
                        IVectorStore<Chunk> vectorStore,
                        EmbeddingSpec embeddingSpec) {
        this.webappConfig = webappConfig;
        this.datastore = datastore;
        this.vectorStore = vectorStore;
//        String sourceManifestLocation = ingestManifestLocation(sourceManifestId);
//        IngestionManifest ingestionManifest = datastore.readObject(sourceManifestLocation, IngestionManifest.class);
        this.embeddingModel = Embedder.createEmbeddingModel(embeddingSpec, webappConfig.openApiKey());
        this.chatModel = createChatModel(webappConfig);
        this.encryptionDelegate = new EncryptionDelegate(webappConfig.symmetricSigningKey());
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
        SessionState sessionState = getSessionState(request);
        String userPrompt = request.userPrompt();
        List<Chunk> chunksForPrompt = chunksForPrompt(userPrompt, sessionState);
        Map<String, Chunk> lookup = new HashMap<>();
        List<DataSource> dataSourcesForPrompt = lossyTransform(chunksForPrompt, lookup);
        String prompt = createPrompt(dataSourcesForPrompt, sessionState.promptExchanges(), userPrompt);
        System.out.println("prompt: " + prompt);
        String chatModelResponseJson = chatModel.chat(prompt);
        System.out.println("chatModelResponseJson: " + chatModelResponseJson);
        ChatModelResponse chatModelResponse = JsonUtils.toObject(chatModelResponseJson, ChatModelResponse.class); //todo exception handling
        List<String> chunkIdsUsed = chunkIdsUsed(chatModelResponse, lookup);
        List<String> sourceUrls = sourceUrls(chatModelResponse, lookup);
        String chatResponse = chatModelResponse.response();
        List<String> chunkIdsAvailable = chunksForPrompt.stream().map(Chunk::id).collect(Collectors.toList());
        sessionState.promptExchanges().add(new PromptExchange(userPrompt, chatResponse, chunkIdsAvailable, chunkIdsUsed));
        String sessionStateJson = JsonUtils.toJson(sessionState);
        if (encryptSessionState) {
            sessionStateJson = encryptionDelegate.encrypt(sessionStateJson);
        }
        return new Response(chatResponse, sourceUrls, sessionStateJson, prompt + "\n\n" + chatModelResponseJson);
    }

    private List<Chunk> chunksForPrompt(String userPrompt, SessionState sessionState) {
        //userPrompt = chatModel.chat("could you please expand on this prompt in the content of portland city codes: " + userPrompt);
        String vectorStoreQuery = userPrompt;//createVectorStoreQuery(sessionState, userPrompt);
        float[] queryVector = embeddingModel.embed(vectorStoreQuery).content().vector();
        List<VectorMatch<Chunk>> vectorMatches = vectorStore.get(queryVector, webappConfig.chunksToProvide());
        Map<String, Chunk> chunksForPrompt = new LinkedHashMap<>();
        for (VectorMatch<Chunk> vectorMatch : vectorMatches) {
            Chunk chunk = vectorMatch.record();
            chunksForPrompt.put(chunk.id(), chunk);
        }
        for (PromptExchange promptExchange : sessionState.promptExchanges) {
            for (String chunkId : promptExchange.chunkIdsUsed) {
                if (!chunksForPrompt.containsKey(chunkId)) {
                    Chunk chunk = vectorStore.get(chunkId);
                    if (chunk != null) {  //incase a hallucination makes it into sessionState
                        chunksForPrompt.put(chunk.id(), chunk);
                    } else {
                        System.out.println("Chunk could not be found for id = " + chunkId);
                    }
                }
            }
        }
        return new ArrayList<>(chunksForPrompt.values());
    }

    private SessionState getSessionState(Request request) {
        String sessionStateJson = request.sessionState();
        if (sessionStateJson == null) {
            return new SessionState(new ArrayList<>());
        }
        if (encryptSessionState) {
            sessionStateJson = encryptionDelegate.decrypt(sessionStateJson);
        }
        SessionState sessionState = JsonUtils.toObject(sessionStateJson, SessionState.class);
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

    private List<DataSource> lossyTransform(List<Chunk> chunksForPrompt, Map<String, Chunk> lookup) {
        List<DataSource> dataSources = new ArrayList<>();
        for (Chunk chunk : chunksForPrompt) {
            String id = UUID.randomUUID().toString(); //prefer random to chunk.id() as chunk.id() can be surmised and therefore hallucinated
            String chunkText = datastore.readString(chunk.textLocation());
            dataSources.add(new DataSource(id, chunkText));
            lookup.put(id, chunk);
        }
        return dataSources;
    }

    private List<String> chunkIdsUsed(ChatModelResponse chatModelResponse, Map<String, Chunk> lookup) {
        List<String> chunkIds = new ArrayList<>();
        for (String dataSourceKey : chatModelResponse.dataSourcesUsed()) {
            Chunk chunk = lookup.get(dataSourceKey);
            if (chunk == null) { //hallucination (source cited was not part of prompt)
                chunkIds.add("hallucination:" + dataSourceKey);
            } else {
                chunkIds.add(chunk.id());
            }
        }
        return chunkIds;
    }

    private List<String> sourceUrls(ChatModelResponse chatModelResponse, Map<String, Chunk> lookup) {
        Set<String> sources = new HashSet<>();
        for (String dataSourceKey : chatModelResponse.dataSourcesUsed()) {
            Chunk chunk = lookup.get(dataSourceKey);
            if (chunk == null) { //hallucination (source cited was not part of prompt)
                sources.add("hallucination:" + dataSourceKey);
            } else {
                sources.add(chunk.sourceRecord().sourceUrl());
            }
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

    private record PromptExchange(String prompt, String response, List<String> chunkIdsAvailable, List<String> chunkIdsUsed) {}

    private record DataSource(String id, String text) {} //used in prompt todo rename?


}
