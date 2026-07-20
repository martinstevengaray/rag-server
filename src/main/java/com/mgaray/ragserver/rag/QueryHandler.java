package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;

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


}
