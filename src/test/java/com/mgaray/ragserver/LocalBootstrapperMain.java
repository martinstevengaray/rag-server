package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
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

    private static final int numberOfEmbeddingThreads = 10;
    private static final EmbeddingModelType embeddingModelType = EmbeddingModelType.BGE_SMALL_EN_V15_QUANTIZED; //OPEN_AI_TEXT_EMBEDDING_3_SMALL;
    private static final ChunkingSpec chunkingSpec = new ChunkingSpec(500, 0.5f);

    private static final String ingestManifestId = BootstrapperMain.portlandIngestManifestId;
    private static final String sourceCatalogLocation = BootstrapperMain.portlandSourceCatalogLocation;
    private static final String localSourceRoot = "local/sources";
    public static final String localIngestionRoot = "local/s3bucket";

    public static void main(String[] args) {
        String openAiApiKey = BootstrapperMain.readConfig("local/config.sh", "OPEN_AI_API_KEY");
        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        IDatastore sourceDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, localSourceRoot);

        IDatastore ingestionDatastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore ingestionDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localIngestionRoot);
        DatastoreMonitor datastoreMonitor = new DatastoreMonitor("ingestion store", 10000L);
        ingestionDatastoreMemory = datastoreMonitor.add(ingestionDatastoreMemory, Datastore.Mode.IN_MEMORY);
        ingestionDatastoreDisk = datastoreMonitor.add(ingestionDatastoreDisk, Datastore.Mode.LOCAL_DISK);
        IDatastore ingestionDatastore = new DatastoreCache(ingestionDatastoreMemory, ingestionDatastoreDisk);

        IVectorStore<Chunk> vectorStore = new InMemoryVectorStore<>(Chunk.class);
        RunDefinition runDefinition = new RunDefinition(chunkingSpec, new EmbeddingSpec(embeddingModelType));
        Bootstrapper bootstrapper = new Bootstrapper(config, sourceDatastore, ingestionDatastore, vectorStore);
        bootstrapper.bootstrap(sourceCatalogLocation, ingestManifestId, runDefinition);

    }


}
