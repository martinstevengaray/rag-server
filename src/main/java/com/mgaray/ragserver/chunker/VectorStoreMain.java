package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.V;

import java.util.List;

public class VectorStoreMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        DataFetcher dataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, bucket);
        String sourceManifestLocation = Models.sourceManifestLocation(portlandSourceManifestId);
        Models.SourceManifest sourceManifest = dataFetcher.fetch(sourceManifestLocation, Models.SourceManifest.class);
        VectorStore vectorStore = new VectorStore(dataFetcher);
        vectorStore.load(sourceManifest);

        //test:
        EmbeddingModel embeddingModel = Embedder.createModel(Models.ModelType.DUMMY);
        String searchQuery = "street parking";
        float[] searchVector = embeddingModel.embed(searchQuery).content().vector();
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = dataFetcher.fetch(chunk.textLocation());
            System.out.println(("\n\n--------------------------------------------------------------\n\n"));
            System.out.println(chunkText);
        }

    }

}
