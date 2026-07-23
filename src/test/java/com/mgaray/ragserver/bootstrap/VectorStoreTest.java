package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.WebappMain;
import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public class VectorStoreTest {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";

    public static void main(String[] args) {
        IDatastore datastore = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        IVectorStore<Chunk> vectorStore = InMemoryVectorStore.load(datastore, portlandSourceManifestId, Chunk.class);
        String openAiApiKey = WebappMain.readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
        EmbeddingModel embeddingModel =
                Embedder.createEmbeddingModel(new EmbeddingSpec(EmbeddingModelType.OPEN_AI_TEXT_EMBEDDING_3_SMALL), openAiApiKey);
        String searchQuery = "street parking";
        float[] searchVector = embeddingModel.embed(searchQuery).content().vector();
        List<IVectorStore.VectorRecord<Chunk>> vectorRecords = vectorStore.get(searchVector, 5);
        for (IVectorStore.VectorRecord<Chunk> vectorRecord : vectorRecords) {
            Chunk chunk = vectorRecord.t();
            String chunkText = datastore.readString(chunk.textLocation());
            System.out.println(("\n\n--------------------------------------------------------------\n\n"));
            System.out.println(chunkText);
        }
    }
}
