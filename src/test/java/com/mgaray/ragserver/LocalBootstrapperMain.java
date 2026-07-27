package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.TieredDatastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.InMemoryDatastore;
import com.mgaray.ragserver.awsresources.LocalDiskDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.localrunutils.DatastoreMonitor;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;
import com.mgaray.ragserver.common.Models.Chunk;

public class LocalBootstrapperMain {

    private static final String localSourceRoot = "local/sources";

    public static void main(String[] args) {
        ChunkingSpec chunkingSpec = BootstrapperMain.chunkingSpec;
        int numberOfEmbeddingThreads = BootstrapperMain.numberOfEmbeddingThreads;
        EmbeddingModelType embeddingModelType = BootstrapperMain.embeddingModelType;
        String ingestManifestId = BootstrapperMain.ingestManifestId;
        String sourceCatalogLocation = BootstrapperMain.sourceCatalogLocation;
        String localIngestionRoot = BootstrapperMain.localIngestionRoot;
        String openAiApiKey = BootstrapperMain.readConfig("local/config.sh", "OPEN_AI_API_KEY");

        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new LocalDiskDatastore(localSourceRoot);

        IDatastore ingestionDatastoreMemory = new InMemoryDatastore();
        IDatastore ingestionDatastoreDisk = new LocalDiskDatastore(localIngestionRoot);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory, Datastore.Mode.IN_MEMORY);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk, Datastore.Mode.LOCAL_DISK);
        IDatastore ingestionDatastore = new TieredDatastore(ingestionDatastoreMemory, ingestionDatastoreDisk);

        IVectorStore<Chunk> vectorStore = new InMemoryVectorStore<>(Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper = new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStore);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);

    }


}
