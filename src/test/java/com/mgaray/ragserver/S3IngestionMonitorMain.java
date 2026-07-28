package com.mgaray.ragserver;

import com.mgaray.ragserver.ingest.IngestionPipeline;
import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import com.mgaray.ragserver.storage.vector.S3VectorStore;

public class S3IngestionMonitorMain {

    public static void main(String[] args) {
        ChunkingSpec chunkingSpec = IngestionMain.chunkingSpec;
        int numberOfEmbeddingThreads = IngestionMain.numberOfEmbeddingThreads;
        EmbeddingModelType embeddingModelType = IngestionMain.embeddingModelType;
        String ingestionManifestId = IngestionMain.ingestionManifestId;
        String sourceCatalogLocation = IngestionMain.sourceCatalogLocation;
        String localIngestionRoot = IngestionMain.localIngestionRoot;
        String s3SourceBucket = IngestionMain.s3SourceBucket;
        String s3IngestionBucket = IngestionMain.s3IngestionBucket;;
        String s3VectorStoreBucket = IngestionMain.s3VectorStoreBucket;;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");

        Models.IngestionConfig config = new Models.IngestionConfig(numberOfEmbeddingThreads, openAiApiKey);

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
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestionManifestId, Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        IngestionPipeline ingestionPipeline =
                new IngestionPipeline(config, sourceDatastore, ingestionDatastore, vectorStoreMemory, vectorStoreS3);
        ingestionPipeline.run(sourceCatalogLocation, ingestionManifestId, runDefinition);
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
