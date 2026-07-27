package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;

public class S3BootstrapperMonitorMain {

    private static final ChunkingSpec chunkingSpec = BootstrapperMain.chunkingSpec;
    private static final int numberOfEmbeddingThreads = BootstrapperMain.numberOfEmbeddingThreads;
    private static final EmbeddingModelType embeddingModelType = BootstrapperMain.embeddingModelType;
    private static final String ingestManifestId = BootstrapperMain.ingestManifestId;
    private static final String sourceCatalogLocation = BootstrapperMain.sourceCatalogLocation;
    private static final String localIngestionRoot = BootstrapperMain.localIngestionRoot;
    private static final String s3SourceBucket = BootstrapperMain.s3SourceBucket;
    private static final String s3IngestionBucket = BootstrapperMain.s3IngestionBucket;;
    private static final String s3VectorStoreBucket = BootstrapperMain.s3VectorStoreBucket;;


    public static void main(String[] args) {
        String openAiApiKey = BootstrapperMain.readConfig("local/config.sh", "OPEN_AI_API_KEY");
        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new Datastore(Datastore.Mode.S3, s3SourceBucket);

        IDatastore ingestionDatastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore ingestionDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localIngestionRoot);
        IDatastore ingestionDatastoreS3 = new Datastore(Datastore.Mode.S3, s3IngestionBucket);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory, Datastore.Mode.IN_MEMORY);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk, Datastore.Mode.LOCAL_DISK);
        ingestionDatastoreS3 = datastoreMonitor.add(ingestionDatastoreS3, Datastore.Mode.S3);
        IDatastore ingestionDatastore =
                new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk, ingestionDatastoreS3);

        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestManifestId, Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper =
                new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStoreMemory, vectorStoreS3);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);
    }

}
