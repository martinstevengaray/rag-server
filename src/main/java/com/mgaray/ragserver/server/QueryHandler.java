package com.mgaray.ragserver.server;

import com.mgaray.ragserver.crypto.EncryptionDelegate;
import com.mgaray.ragserver.logger.ILogger;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.ingest.Embedder;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.util.JsonUtils;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.WebappConfig;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.VectorQueryConfig;
import com.mgaray.ragserver.Models.Request;
import com.mgaray.ragserver.Models.Response;
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
        this.embeddingModel = Embedder.createEmbeddingModel(embeddingSpec, webappConfig.openAiKey());
        this.chatModel = createChatModel(webappConfig);
        this.encryptionDelegate = new EncryptionDelegate(webappConfig.symmetricSigningKey());
    }

    //to support unit tests
    QueryHandler(WebappConfig webappConfig,
                 IDatastore datastore,
                 IVectorStore<Chunk> vectorStore,
                 EmbeddingModel embeddingModel,
                 ChatModel chatModel,
                 EncryptionDelegate encryptionDelegate) {
        this.webappConfig = webappConfig;
        this.datastore = datastore;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.encryptionDelegate = encryptionDelegate;
    }

    public static ChatModel createChatModel(WebappConfig config) {
        return switch(config.chatModelType()) {
            case OPEN_AI_GPT_4O_MINI -> OpenAiChatModel.builder()
                    .apiKey(config.openAiKey())
                    .modelName("gpt-4o-mini")
                    .temperature(0.0)
                    .build();
            case OPEN_AI_GPT_4O -> OpenAiChatModel.builder()
                    .apiKey(config.openAiKey())
                    .modelName("gpt-4o")
                    .temperature(0.0)
                    .build();
            case OPEN_AI_GPT_56_SOL -> OpenAiChatModel.builder()
                    .apiKey(config.openAiKey())
                    .modelName("gpt-5.6-sol")  // "gpt-5.6-sol" does not support temperature!=1, defaults to 1
                    .build();
            case OPEN_AI_GPT_5_NANO -> OpenAiChatModel.builder()
                    .apiKey(config.openAiKey())
                    .modelName("gpt-5-nano")  // "gpt-5.6-nano" does not support temperature!=1, defaults to 1
                    .reasoningEffort("minimal")
                    .build();
        };
    }

    public Response query(Request request, ILogger logger) {
        Timer timer = new Timer(logger);
        SessionState sessionState = getSessionState(request);
        String userPrompt = request.userPrompt();
        timer.snap("Extract state and prompt");
        List<Chunk> chunksForPrompt = chunksForPrompt(userPrompt, sessionState, logger);
        timer.snap("Retrieve chunksForPrompt");
        Map<String, Chunk> lookup = new HashMap<>();
        List<PromptDataSource> promptDataSources = promptDataSources(chunksForPrompt, lookup);
        timer.snap("Retrieve chunk text");
        String prompt = createPrompt(promptDataSources, sessionState.promptExchanges(), userPrompt);
        logger.log("prompt: " + prompt);
        timer.snap("Create raw prompt");
        String chatModelResponseJson = chatModel.chat(prompt);
        timer.snap("Chat response.");
        logger.log("chatModelResponseJson: " + chatModelResponseJson);
        List<String> sourceUrls = null;
        String chatResponse = null;
        try {
            chatModelResponseJson = extractJsonFromText(chatModelResponseJson);
            ChatModelResponse chatModelResponse = JsonUtils.toObject(chatModelResponseJson, ChatModelResponse.class);
            List<String> chunkIdsUsed = chunkIdsUsed(chatModelResponse, lookup);
            sourceUrls = sourceUrls(chatModelResponse, lookup);
            chatResponse = chatModelResponse.response();
            List<String> chunkIdsAvailable = chunksForPrompt.stream().map(Chunk::id).collect(Collectors.toList());
            sessionState.promptExchanges().add(new PromptExchange(userPrompt, chatResponse, chunkIdsAvailable, chunkIdsUsed));
            timer.snap("Interpret chat response.");
        } catch (Exception e) {
            logger.error("Exception in query", e);
            sourceUrls = List.of();
            chatResponse = "Unable to parse response.";
        }
        String sessionStateJson = JsonUtils.toJson(sessionState);
        if (encryptSessionState) {
            sessionStateJson = encryptionDelegate.encrypt(sessionStateJson);
        }
        timer.snap("Prepare session state for response");
        return new Response(chatResponse, sourceUrls, sessionStateJson, prompt + "\n\n" + chatModelResponseJson);
    }

    private List<Chunk> chunksForPrompt(String userPrompt, SessionState sessionState, ILogger logger) {
        VectorQueryConfig vectorQueryConfig = webappConfig.vectorQueryConfig();
        List<VectorMatch<Chunk>> vectorMatches = new ArrayList<>();
        String conversationVectorStoreQuery = createConversationVectorStoreQuery(sessionState, userPrompt);
        float[] conversationQueryVector = embeddingModel.embed(conversationVectorStoreQuery).content().vector();
        vectorMatches.addAll(vectorStore.get(conversationQueryVector, vectorQueryConfig.conversationChunkCount()));
        float[] userPromptQueryVector = embeddingModel.embed(userPrompt).content().vector();
        vectorMatches.addAll(vectorStore.get(userPromptQueryVector, vectorQueryConfig.mostRecentPromptChunkCount()));
        Map<String, Chunk> chunksForPrompt = new LinkedHashMap<>();
        for (VectorMatch<Chunk> vectorMatch : vectorMatches) {
            Chunk chunk = vectorMatch.record();
            chunksForPrompt.put(chunk.id(), chunk);
        }
        int max = vectorQueryConfig.conversationPreviouslyUsedChunkMaxCount();
        for (PromptExchange promptExchange : sessionState.promptExchanges()) {
            for (String chunkId : promptExchange.chunkIdsUsed()) {
                if (!chunksForPrompt.containsKey(chunkId)) {
                    Chunk chunk = vectorStore.get(chunkId);
                    if (chunk != null) {  //incase a hallucination makes it into sessionState
                        chunksForPrompt.put(chunk.id(), chunk);
                        if (--max <= 0) {
                            return new ArrayList<>(chunksForPrompt.values());
                        }
                    } else {
                        logger.error("Chunk could not be found for id = " + chunkId);
                    }
                }
            }
        }
        return new ArrayList<>(chunksForPrompt.values());
    }

    private String extractJsonFromText(String text) {
        int firstIndex = text.indexOf("{");
        int lastIndex = text.lastIndexOf("}");
        return text.substring(firstIndex, lastIndex+1);
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

    private String createConversationVectorStoreQuery(SessionState sessionState, String userPrompt) {
        StringBuilder stringBuilder = new StringBuilder();
        for (PromptExchange promptExchange : sessionState.promptExchanges()) {
            stringBuilder.append(promptExchange.prompt() + "\n");
            stringBuilder.append(promptExchange.response() + "\n");
        }
        stringBuilder.append(userPrompt);
        return stringBuilder.toString();
    }

    private List<PromptDataSource> promptDataSources(List<Chunk> chunksForPrompt, Map<String, Chunk> lookup) {
        List<PromptDataSource> promptDataSources = new ArrayList<>();
        for (Chunk chunk : chunksForPrompt) { //prefer random to chunk.id() to prevent hallucinating sources
            String id = UUID.randomUUID().toString();
            String chunkText = datastore.readString(chunk.textLocation());
            promptDataSources.add(new PromptDataSource(id, chunkText));
            lookup.put(id, chunk);
        }
        return promptDataSources;
    }

    private List<String> chunkIdsUsed(ChatModelResponse chatModelResponse, Map<String, Chunk> lookup) {
        List<String> chunkIds = new ArrayList<>();
        for (String dataSourceKey : chatModelResponse.dataSourcesUsed()) {
            Chunk chunk = lookup.get(dataSourceKey);
            if (chunk == null) { //hallucination (source cited was not part of prompt)
                chunkIds.add("not part of source data:" + dataSourceKey);
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

    private String createPrompt(List<PromptDataSource> promptDataSources,
                                List<PromptExchange> promptExchanges,
                                String userPrompt) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(PROMPT_PREFIX);
        prompt.append("DATA SOURCES:\n");
        for (PromptDataSource promptDataSource : promptDataSources) {
            prompt.append(JsonUtils.toJson(promptDataSource) + "\n");
        }
        for (PromptExchange promptExchange : promptExchanges) {
            prompt.append("\nPROMPT:\n");
            prompt.append("     " + promptExchange.prompt());
            prompt.append("\nRESPONSE:\n");
            prompt.append("     " + promptExchange.response());
        }
        prompt.append("\nPROMPT:\n");
        prompt.append("     " + userPrompt);
        return prompt.toString();
    }

    private static final String PROMPT_PREFIX = """
Use the following data sources only to continue the conversation.
If the source data does not include data to answer the prompt, say so.
Include the ids of the data sources you used to form your response.
Always respond in the following json format, without a prefix or suffix:
{ "dataSourcesUsed": ["<id1>","<id2>","<id3>",...], "response": "<next response>" }
Do not add references inline in the response, only in the dataSourcesUsed section.
""";

    private record ChatModelResponse(List<String> dataSourcesUsed,
                                     String response) {}

    private record SessionState(List<PromptExchange> promptExchanges) {}

    private record PromptExchange(String prompt,
                                  String response,
                                  List<String> chunkIdsAvailable,
                                  List<String> chunkIdsUsed) {}

    private record PromptDataSource(String id, String text) {}


}
