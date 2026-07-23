package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.ChunkMatch;
import com.mgaray.ragserver.common.Models.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public class VectorStoreTest {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";

    public static void main(String[] args) {
        IDatastore datastore = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        VectorStore vectorStore = VectorStore.load(datastore, portlandSourceManifestId);
        EmbeddingModel embeddingModel =
                Embedder.createEmbeddingModel(new EmbeddingSpec(EmbeddingModelType.BGE_SMALL_EN_V15_QUANTIZED), null);
        String searchQuery = "street parking";
        float[] searchVector = embeddingModel.embed(searchQuery).content().vector();
        List<ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        for (ChunkMatch chunkMatch : chunkMatches) {
            Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.readString(chunk.textLocation());
            System.out.println(("\n\n--------------------------------------------------------------\n\n"));
            System.out.println(chunkText);
        }
    }
}
