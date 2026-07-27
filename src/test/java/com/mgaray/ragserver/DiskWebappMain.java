package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.VectorQueryConfig;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.localrunutils.LocalServer;
import com.mgaray.ragserver.localrunutils.WebappHandler;
import com.mgaray.ragserver.server.QueryHandler;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;

import static com.mgaray.ragserver.common.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class DiskWebappMain {

    private static final String localDiskRoot = "local/s3bucket";
    private static final String sourceManifestId = "portland-city-code";
    private static final String openAiApiKey = BootstrapperMain.readConfig(
            "local/config.sh", "OPEN_AI_API_KEY");
    private static final String symmetricSigningKey = BootstrapperMain.readConfig(
            "local/config.sh", "SYMMETRIC_SIGNING_KEY");

    public static void main(String[] args) throws Exception {
        IDatastore datastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore dataStoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, localDiskRoot);
        IDatastore datastore = new DatastoreCache(datastoreMemory, dataStoreDisk);

        String ingestionManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(ingestionManifestLocation, IngestionManifest.class);

        String inMemoryVectorStoreExportLocation = ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation();
        IVectorStore<Chunk> vectorStoreMemory = InMemoryVectorStore.load(dataStoreDisk, inMemoryVectorStoreExportLocation, Chunk.class);

        VectorQueryConfig vectorQueryConfig = new VectorQueryConfig(10, 10, 10);

        WebappConfig webappConfig = new WebappConfig(OPEN_AI_GPT_4O_MINI, vectorQueryConfig, openAiApiKey, symmetricSigningKey);

        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore, vectorStoreMemory, embeddingSpec);

        LocalServer localServer = new LocalServer();
        localServer.startServer(new WebappHandler(queryHandler), 80);
    }

}
