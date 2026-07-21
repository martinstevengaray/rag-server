package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

public class QueryHandler {

    public final IDatastore datastore;
    public final VectorStore vectorStore;
    public final EmbeddingModel embeddingModel;
    public final ChatModel chatModel;

    public QueryHandler(IDatastore datastore, String openAiApiKey, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        Models.SourceManifest sourceManifest = datastore.readObject(sourceManifestLocation, Models.SourceManifest.class);
        Models.ModelType modelType = sourceManifest.runDefinition().embeddingSpec().modelType();
        this.embeddingModel = Embedder.createEmbeddingModel(modelType);
        this.chatModel = createChatModel(openAiApiKey);
    }

    public String query(String queryString) {
        float[] searchVector = embeddingModel.embed(queryString).content().vector();
        vectorStore.get(searchVector, 5);
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 50); //todo count hardcode
        StringBuilder stringBuilder = new StringBuilder();
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.readString(chunk.textLocation());
            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
            stringBuilder.append(chunkText);
        }
        stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
        String prompt = "only use the the following chunks of data to answer the question presented at the end." +
                "If unable to answer the question based on the chunk sources say so.\n\n" +
                "chunks: " + stringBuilder.toString() + "\n\n" +
                "question to answer: " + queryString;
        return chatModel.chat(prompt) + "\n\n\n\n" + prompt;
    }

    public static ChatModel createChatModel(String openAiApiKey) {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-5.6-sol") //todo hardcoded //"gpt-4o-mini") // also consider "gpt-4o"     "gpt-5.6") //"gpt-5.6-sol")  //"
                //.temperature(0.0)         // 0.0 = deterministic output , "gpt-5.6-sol" does not support temperature!=1
                .build();
    }

}
