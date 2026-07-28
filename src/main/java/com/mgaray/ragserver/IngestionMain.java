package com.mgaray.ragserver;

import com.mgaray.ragserver.ingest.IngestionPipeline;
import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import com.mgaray.ragserver.Models.IngestionConfig;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.storage.vector.S3VectorStore;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;

public class IngestionMain {

    public static final ChunkingSpec chunkingSpec = new ChunkingSpec(500, 0.5f);
    public static final int numberOfEmbeddingThreads = 10;
    public static final EmbeddingModelType embeddingModelType = EmbeddingModelType.OPEN_AI_TEXT_EMBEDDING_3_LARGE;
    public static final String ingestionManifestId = IngestionMain.portlandIngestionManifestId;
    public static final String sourceCatalogLocation = IngestionMain.portlandSourceCatalogLocation;

    public static final String localIngestionRoot = "local/s3bucket";
    public static final String s3SourceBucket = "rag-server-source";
    public static final String s3IngestionBucket = "rag-server-ingestion";
    public static final String s3VectorStoreBucket = "rag-server-vector";
    public static final String portlandIngestionManifestId = "portland-city-code";
    public static final String portlandSourceCatalogLocation = "portland-city-code/sourceCatalog.json";
    public static final String oregonIngestionManifestId = "oregon-state-code";
    public static final String oregonSourceCatalogLocation = "oregon-state-code/sourceCatalog.json";

    public static void main(String[] args) {
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        IngestionConfig config = new IngestionConfig(numberOfEmbeddingThreads, openAiApiKey);
        IDatastore sourceDatastore = new S3Datastore(s3SourceBucket);
        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreS3 = new S3Datastore(s3IngestionBucket);
        IDatastore ingestionDatastore = new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreS3);
        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestionManifestId, Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        IngestionPipeline ingestionPipeline =
                new IngestionPipeline(config, sourceDatastore, ingestionDatastore, vectorStoreMemory, vectorStoreS3);
        ingestionPipeline.run(sourceCatalogLocation, ingestionManifestId, runDefinition);
    }
}
/*

portland-city-code complete in 26 minutes

 */
