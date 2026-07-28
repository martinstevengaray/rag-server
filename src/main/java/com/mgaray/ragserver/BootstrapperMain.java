package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import com.mgaray.ragserver.Models.BootstrapperConfig;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.storage.vector.S3VectorStore;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;

public class BootstrapperMain {

    public static final ChunkingSpec chunkingSpec = new ChunkingSpec(500, 0.5f);
    public static final int numberOfEmbeddingThreads = 10;
    public static final EmbeddingModelType embeddingModelType = EmbeddingModelType.OPEN_AI_TEXT_EMBEDDING_3_LARGE;
    public static final String ingestManifestId = BootstrapperMain.portlandIngestManifestId;
    public static final String sourceCatalogLocation = BootstrapperMain.portlandSourceCatalogLocation;

    public static final String localIngestionRoot = "local/s3bucket";
    public static final String s3SourceBucket = "rag-server-source";
    public static final String s3IngestionBucket = "rag-server-ingestion";
    public static final String s3VectorStoreBucket = "rag-server-vector";
    public static final String portlandIngestManifestId = "portland-city-code";
    public static final String portlandSourceCatalogLocation = "portland-city-code/sourceCatalog.json";
    public static final String oregonIngestManifestId = "oregon-state-code";
    public static final String oregonSourceCatalogLocation = "oregon-state-code/sourceCatalog.json";

    public static void main(String[] args) {
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);
        IDatastore sourceDatastore = new S3Datastore(s3SourceBucket);
        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreS3 = new S3Datastore(s3IngestionBucket);
        IDatastore ingestionDatastore = new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreS3);
        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestManifestId, Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper =
                new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStoreMemory, vectorStoreS3);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);
    }
}
/*

portland-city-code complete in 26 minutes

 */





//    public static String readConfig(String configFilename, String key) {
//        try {
//            List<String> lines = Files.readAllLines(Path.of(configFilename));
//            for (String line : lines) {
//                String prefix = "export " + key + "=";
//                if (line.startsWith(prefix)) {
//                    return line.substring(prefix.length()).split("\"")[1];
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        throw new IllegalArgumentException(key +" not found in " + configFilename);
//    }



//    private static final String localSourceRoot = "/Users/turtlemccully/projects/rag-server/local/sources";
//    private static final String localIngestionRoot = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
//    private static final String s3SourceBucket = "rag-server-source";
//    private static final String s3IngestionBucket = "rag-server-ingestion";
//    private static final String s3VectorStoreBucket = "rag-server-vector";
//
//    public static final String portlandIngestManifestId = "portland-city-code";
//    public static final String portlandSourceCatalogLocation = "portland-city-code/sourceCatalog.json";


//    private static final String sourceBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
//    private static final String outBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
//
//    private static final String oregonIngestManifestId = "oregon-state-code";
//    private static final String websourceIngestManifestId = "web-catholic-bible";
//    private static final String nabIngestManifestId = "new-american-bible";

//    public static void main(String[] args) {
//        String ingestManifestId = portlandIngestManifestId;
//        int numberOfEmbeddingThreads = 10;
//        String openAiApiKey = readKeyFromConfig(
//                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
//        BootstrapperConfig bootstrapperConfig = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);
//
//        IDatastore sourceDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localSourceRoot);
//        IDatastore sourceDatastoreS3 = new Datastore(Datastore.Mode.S3, s3SourceBucket);
//
//        IDatastore ingestionDatastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
//        IDatastore ingestionDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localIngestionRoot);
//        IDatastore ingestionDatastoreS3 = new Datastore(Datastore.Mode.S3, s3IngestionBucket);
//
//        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store").start(10000L);
//        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory, Datastore.Mode.IN_MEMORY);
//        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk, Datastore.Mode.LOCAL_DISK);
//        ingestionDatastoreS3 = datastoreMonitor.add(ingestionDatastoreS3, Datastore.Mode.S3);
//
//        IDatastore ingestionDatastoreWithCacheDisk = new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk);
//        IDatastore ingestionDatastoreWithCacheS3 = new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk, ingestionDatastoreS3);
//
//        EmbeddingModelType embeddingModelType = EmbeddingModelType.BGE_SMALL_EN_V15_QUANTIZED; //OPEN_AI_TEXT_EMBEDDING_3_SMALL;
//        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
//        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestManifestId, Chunk.class);
//
//        RunDefinition runDefinition = new RunDefinition(
//                new ChunkingSpec(500, 0.5f),
//                new EmbeddingSpec(embeddingModelType));
//        { //in memory vector store
//            Bootstrapper bootstrapper = new Bootstrapper(bootstrapperConfig, sourceDatastoreDisk, ingestionDatastoreWithCacheDisk, vectorStoreMemory);
//            bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
//        }
////        { //s3 vector store
////            Bootstrapper bootstrapper = new Bootstrapper(bootstrapperConfig, sourceDatastoreS3, ingestionDatastoreWithCacheS3, vectorStoreS3);
////            bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
////        }
//    }

