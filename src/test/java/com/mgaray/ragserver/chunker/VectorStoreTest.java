package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.DataStore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public class VectorStoreTest {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "local-embedding-portland-city-code";

    public static void main(String[] args) {
        IDatastore dataStore = new DataStore(DataStore.Mode.ON_DISK, bucket);
        String sourceManifestLocation = Models.sourceManifestLocation(portlandSourceManifestId);
        Models.SourceManifest sourceManifest = dataStore.fetch(sourceManifestLocation, Models.SourceManifest.class);
        VectorStore vectorStore = new VectorStore(dataStore);
        vectorStore.load(sourceManifest);


        EmbeddingModel embeddingModel = Embedder.createModel(Models.ModelType.BGE_SMALL_EN_V15_QUANTIZED);
        String searchQuery = "street parking";
        float[] searchVector = embeddingModel.embed(searchQuery).content().vector();
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = dataStore.fetch(chunk.textLocation());
            System.out.println(("\n\n--------------------------------------------------------------\n\n"));
            System.out.println(chunkText);
        }
    }
}
