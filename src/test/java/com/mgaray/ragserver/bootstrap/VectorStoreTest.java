package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.storage.blob.IDatastore;
import com.mgaray.ragserver.storage.blob.LocalDiskDatastore;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public class VectorStoreTest {

    private static final String localIngestionRoot = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String portlandSourceManifestId = "portland-city-code";

    public static void main(String[] args) {
        IDatastore datastore = new LocalDiskDatastore(localIngestionRoot);
        IVectorStore<Chunk> vectorStore = InMemoryVectorStore.load(datastore, portlandSourceManifestId, Chunk.class);
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        EmbeddingModel embeddingModel = Embedder.createEmbeddingModel(
                new EmbeddingSpec(EmbeddingModelType.OPEN_AI_TEXT_EMBEDDING_3_SMALL), openAiApiKey);
        String searchQuery = "street parking";
        float[] searchVector = embeddingModel.embed(searchQuery).content().vector();
        List<VectorMatch<Chunk>> vectorMatches = vectorStore.get(searchVector, 5);
        for (VectorMatch<Chunk> vectorMatch : vectorMatches) {
            Chunk chunk = vectorMatch.record();
            String chunkText = datastore.readString(chunk.textLocation());
            System.out.println(("\n\n--------------------------------------------------------------\n\n"));
            System.out.println(chunkText);
        }
    }
}
