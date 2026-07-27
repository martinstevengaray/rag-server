package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.DatastoreMonitor;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.vectorstore.S3VectorStore;

public class BootstapperMain {

    private static final String localSourceRoot = "/Users/turtlemccully/projects/rag-server/local/sources";
    private static final String localIngestionRoot = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String s3SourceBucket = "rag-server-source";
    private static final String s3IngestionBucket = "rag-server-ingestion";
    private static final String s3VectorStoreBucket = "rag-server-vector";

    private static final String portlandIngestManifestId = "portland-city-code";
    private static final String portlandSourceCatalogLocation = "portland-city-code/sourceCatalog.json";


//    private static final String sourceBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
//    private static final String outBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
//
//    private static final String oregonIngestManifestId = "oregon-state-code";
//    private static final String websourceIngestManifestId = "web-catholic-bible";
//    private static final String nabIngestManifestId = "new-american-bible";

    public static void main(String[] args) {
        String ingestManifestId = portlandIngestManifestId;
        int numberOfEmbeddingThreads = 10;
        String openAiApiKey = WebappMain.readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
        BootstrapperConfig bootstrapperConfig = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localSourceRoot);
        IDatastore sourceDatastoreS3 = new Datastore(Datastore.Mode.S3, s3SourceBucket);

        IDatastore ingestionDatastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore ingestionDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localIngestionRoot);
        IDatastore ingestionDatastoreS3 = new Datastore(Datastore.Mode.S3, s3IngestionBucket);

        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store").start(10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory, Datastore.Mode.IN_MEMORY);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk, Datastore.Mode.LOCAL_DISK);
        ingestionDatastoreS3 = datastoreMonitor.add(ingestionDatastoreS3, Datastore.Mode.S3);

        IDatastore ingestionDatastoreWithCacheDisk = new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk);
        IDatastore ingestionDatastoreWithCacheS3 = new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk, ingestionDatastoreS3);

        EmbeddingModelType embeddingModelType = EmbeddingModelType.BGE_SMALL_EN_V15_QUANTIZED; //OPEN_AI_TEXT_EMBEDDING_3_SMALL;
        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestManifestId, Chunk.class);

        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(500, 0.5f),
                new EmbeddingSpec(embeddingModelType));
        { //in memory vector store
            Bootstrapper bootstrapper = new Bootstrapper(bootstrapperConfig, sourceDatastoreDisk, ingestionDatastoreWithCacheDisk, vectorStoreMemory);
            bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
        }
//        { //s3 vector store
//            Bootstrapper bootstrapper = new Bootstrapper(bootstrapperConfig, sourceDatastoreS3, ingestionDatastoreWithCacheS3, vectorStoreS3);
//            bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
//        }
    }

}
