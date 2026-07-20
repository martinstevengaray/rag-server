package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

public class QueryHandler {

    public final IDatastore datastore;
    public final VectorStore vectorStore;
    public final EmbeddingModel embeddingModel;

    public QueryHandler(IDatastore datastore, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        Models.SourceManifest sourceManifest = datastore.fetch(sourceManifestLocation, Models.SourceManifest.class);
        Models.ModelType modelType = sourceManifest.runDefinition().embeddingSpec().modelType();
        this.embeddingModel = Embedder.createModel(modelType);
    }

    public String query(String queryString) {
        float[] searchVector = embeddingModel.embed(queryString).content().vector();
        vectorStore.get(searchVector, 5);
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        StringBuilder stringBuilder = new StringBuilder();
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.fetch(chunk.textLocation());
            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
            stringBuilder.append(chunkText);
        }
        return stringBuilder.toString();
    }


    private static String callOpenAi(String prompt) {
        // Configure the client. The API key is read from the OPENAI_API_KEY environment
        // variable (same convention as OpenAiEmbeddingModel in Embedder) so the secret
        // never lives in source. Build this once per call for simplicity; if you make
        // many requests, hoist it into a field since the model is thread-safe and reusable.
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini") // cheap + fast; swap for "gpt-4o" if you need stronger reasoning
                .temperature(0.0)         // deterministic output for RAG-style answers
                .build();

        // Send the prompt and return the model's text response.
        return model.chat(prompt);
    }


    private static String callOpenAiWithOpenAiSdk(String prompt) {
        return null;
    }

}
