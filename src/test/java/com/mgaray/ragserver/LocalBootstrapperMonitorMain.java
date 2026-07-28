package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;
import com.mgaray.ragserver.Models.BootstrapperConfig;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.Chunk;

public class LocalBootstrapperMonitorMain {

    private static final String localSourceRoot = "local/sources";

    public static void main(String[] args) {
        ChunkingSpec chunkingSpec = BootstrapperMain.chunkingSpec;
        int numberOfEmbeddingThreads = BootstrapperMain.numberOfEmbeddingThreads;
        EmbeddingModelType embeddingModelType = BootstrapperMain.embeddingModelType;
        String ingestManifestId = BootstrapperMain.ingestManifestId;
        String sourceCatalogLocation = BootstrapperMain.sourceCatalogLocation;
        String localIngestionRoot = BootstrapperMain.localIngestionRoot;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");

        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new LocalDiskDatastore(localSourceRoot);

        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreDisk = new LocalDiskDatastore(localIngestionRoot);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk);
        IDatastore ingestionDatastore = new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreDisk);

        IVectorStore<Chunk> vectorStore = new InMemoryVectorStore<>(Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper = new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStore);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);

    }


}
