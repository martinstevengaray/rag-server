package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Embedder;
import com.mgaray.ragserver.bootstrap.VectorStore;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.ModelType;
import com.mgaray.ragserver.common.Models.ChunkMatch;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.util.ArrayList;
import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class QueryHandler {

    public final IDatastore datastore;
    public final VectorStore vectorStore;
    public final EmbeddingModel embeddingModel;
    public final ChatModel chatModel;

    public QueryHandler(IDatastore datastore, String openAiApiKey, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        String sourceManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(sourceManifestLocation, IngestionManifest.class);
        ModelType modelType = ingestionManifest.runDefinition().embeddingSpec().modelType();
        this.embeddingModel = Embedder.createEmbeddingModel(modelType);
        this.chatModel = createChatModel(openAiApiKey);
    }

    public static ChatModel createChatModel(String openAiApiKey) {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o-mini") //gpt-5.6-sol") //todo hardcoded //"gpt-4o-mini") // also consider "gpt-4o"     "gpt-5.6") //"gpt-5.6-sol")  //"
                //.temperature(0.0)         // 0.0 = deterministic output , "gpt-5.6-sol" does not support temperature!=1
                .build();
    }

//    public Response queryOLD(Request request) {
//        String userPrompt = request.userPrompt();
//        float[] searchVector = embeddingModel.embed(userPrompt).content().vector();
//        //vectorStore.get(searchVector, 5);
//        List<ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5); //todo count hardcode
//        StringBuilder stringBuilder = new StringBuilder();
//        for (ChunkMatch chunkMatch : chunkMatches) {
//            Chunk chunk = chunkMatch.chunk();
//            String chunkText = datastore.readString(chunk.textLocation());
//            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
//            stringBuilder.append(chunkText);
//        }
//        stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
//        String prompt = "only use the the following chunks of data to answer the question presented at the end." +
//                "If unable to answer the question based on the chunk sources say so.\n\n" +
//                "chunks: " + stringBuilder.toString() + "\n\n" +
//                "question to answer: " + userPrompt;
//        String chatResponse = chatModel.chat(prompt);
//
//        return new Response(chatResponse, request.sessionState(), prompt);
//    }

    public Response query(Request request) {
        String userPrompt = request.userPrompt();
        SessionState sessionState = getSessionState(request);
        String vectorStoreQuery = createVectorStoreQuery(sessionState, userPrompt);
        float[] queryVector = embeddingModel.embed(vectorStoreQuery).content().vector();
        List<ChunkMatch> chunkMatches = vectorStore.get(queryVector, 5);  //todo hardcoded count
        List<DataSource> dataSources = lossyTransform(chunkMatches);
        String prompt = createPrompt(dataSources, sessionState.promptExchanges(), userPrompt);
        String chatModelResponseJson = chatModel.chat(prompt);
        ChatModelResponse chatModelResponse = JsonUtils.toObject(chatModelResponseJson, ChatModelResponse.class); //todo exception handling
        String response = chatModelResponse.response();
        sessionState.promptExchanges().add(new PromptExchange(userPrompt, response, chatModelResponse.dataSourcesUsed()));
        String sessionStateJson = JsonUtils.toJson(sessionState);
        return new Response(chatModelResponse.response(), sessionStateJson, prompt + "\n\n" + chatModelResponseJson);
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

    private List<DataSource> lossyTransform(List<ChunkMatch> chunkMatches) {
        List<DataSource> dataSources = new ArrayList<>();
        for (ChunkMatch chunkMatch : chunkMatches) {
            Chunk chunk = chunkMatch.chunk();
            String dataId = chunk.sourceRecord().id() + "#" + chunk.index();
            String dataText = datastore.readString(chunk.textLocation());
            dataSources.add(new DataSource(dataId, dataText));
        }
        return dataSources;
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
Include the ids of the data sources you used to form your response.
Please respond in the following json format, without a prefix or suffix:
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
