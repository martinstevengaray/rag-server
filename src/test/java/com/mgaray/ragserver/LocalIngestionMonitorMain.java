package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.ingest.IngestionPipeline;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import com.mgaray.ragserver.Models.IngestionConfig;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;

public class LocalIngestionMonitorMain {

    private static final String localSourceRoot = "local/sources";

    public static void main(String[] args) {
        ChunkingSpec chunkingSpec = IngestionMain.chunkingSpec;
        int numberOfEmbeddingThreads = IngestionMain.numberOfEmbeddingThreads;
        EmbeddingModelType embeddingModelType = IngestionMain.embeddingModelType;
        String ingestManifestId = IngestionMain.ingestManifestId;
        String sourceCatalogLocation = IngestionMain.sourceCatalogLocation;
        String localIngestionRoot = IngestionMain.localIngestionRoot;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");

        Models.IngestionConfig config = new IngestionConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new LocalDiskDatastore(localSourceRoot);

        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreDisk = new LocalDiskDatastore(localIngestionRoot);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk);
        IDatastore ingestionDatastore = new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreDisk);

        IVectorStore<Chunk> vectorStore = new InMemoryVectorStore<>(Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        IngestionPipeline ingestionPipeline = new IngestionPipeline(config, sourceDatastore, ingestionDatastore, vectorStore);
        ingestionPipeline.run(sourceCatalogLocation, ingestManifestId, runDefinition);

    }


}
