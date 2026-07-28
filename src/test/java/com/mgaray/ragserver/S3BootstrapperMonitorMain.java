package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.TieredDatastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.InMemoryDatastore;
import com.mgaray.ragserver.awsresources.LocalDiskDatastore;
import com.mgaray.ragserver.awsresources.S3Datastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.SsmDelegate;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;

public class S3BootstrapperMonitorMain {

    public static void main(String[] args) {
        ChunkingSpec chunkingSpec = BootstrapperMain.chunkingSpec;
        int numberOfEmbeddingThreads = BootstrapperMain.numberOfEmbeddingThreads;
        EmbeddingModelType embeddingModelType = BootstrapperMain.embeddingModelType;
        String ingestManifestId = BootstrapperMain.ingestManifestId;
        String sourceCatalogLocation = BootstrapperMain.sourceCatalogLocation;
        String localIngestionRoot = BootstrapperMain.localIngestionRoot;
        String s3SourceBucket = BootstrapperMain.s3SourceBucket;
        String s3IngestionBucket = BootstrapperMain.s3IngestionBucket;;
        String s3VectorStoreBucket = BootstrapperMain.s3VectorStoreBucket;;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");

        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new S3Datastore(s3SourceBucket);

        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreDisk = new LocalDiskDatastore(localIngestionRoot);
        IDatastore ingestionDatastoreS3 = new S3Datastore(s3IngestionBucket);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk);
        ingestionDatastoreS3 = datastoreMonitor.add(ingestionDatastoreS3);
        IDatastore ingestionDatastore =
                new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreDisk, ingestionDatastoreS3);

        IVectorStore<Chunk> vectorStoreMemory = new InMemoryVectorStore<>(Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestManifestId, Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper =
                new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStoreMemory, vectorStoreS3);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);
    }

}


/*

portland-city-code complete
----- ingestion store ----- (final)
LOCAL_DISK: 0r, 6086w, 6085e
IN_MEMORY: 9158r, 6086w, 6085e
S3: 0r, 6086w, 6085e
embedding cost = 24 cents

 */
