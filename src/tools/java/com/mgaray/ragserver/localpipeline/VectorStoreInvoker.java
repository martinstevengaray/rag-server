package com.mgaray.ragserver.localpipeline;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.ingest.Embedder;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public class VectorStoreInvoker {

    private static final String localIngestionRoot = "local/s3bucket";
    private static final String portlandSourceManifestId = "portland-city-code";

    public static void main(String[] args) {
        IDatastore datastore = new LocalDiskDatastore(localIngestionRoot);

        Models.IngestionManifest ingestionManifest = datastore.readIngestionManifest(portlandSourceManifestId);
        String inMemoryVectorStoreExportLocation =
                ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation();
        EmbeddingModelType embeddingModelType = ingestionManifest.runDefinition().embeddingSpec().embeddingModelType();

        IVectorStore<Chunk> vectorStore =
                InMemoryVectorStore.load(datastore, inMemoryVectorStoreExportLocation, Chunk.class);
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        EmbeddingModel embeddingModel = Embedder.createEmbeddingModel(
                new EmbeddingSpec(embeddingModelType), openAiApiKey);
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
