package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.VectorQueryConfig;
import com.mgaray.ragserver.Models.WebappConfig;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.ChatModelType;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.localserver.LocalServer;
import com.mgaray.ragserver.localserver.WebappHandler;
import com.mgaray.ragserver.server.QueryHandler;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.InMemoryVectorStore;

public class LocalWebappMain {

    private static final String ingestionManifestId = IngestionMain.ingestionManifestId;
    private static final ChatModelType chatModelType = ChatModelType.OPEN_AI_GPT_5_NANO;

    public static void main(String[] args) throws Exception {
        String localIngestionRoot = IngestionMain.localIngestionRoot;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        String symmetricSigningKey = SsmDelegate.getParameterFromLocalConfig("SYMMETRIC_SIGNING_KEY");

        IDatastore datastoreMemory = new InMemoryDatastore();
        IDatastore datastoreDisk = new LocalDiskDatastore(localIngestionRoot);
        IDatastore datastore = new TieredDatastore(datastoreMemory, datastoreDisk);

        IngestionManifest ingestionManifest = datastore.readIngestionManifest(ingestionManifestId);;

        String inMemoryVectorStoreExportLocation =
                ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation();
        IVectorStore<Chunk> vectorStoreMemory =
                InMemoryVectorStore.load(datastoreDisk, inMemoryVectorStoreExportLocation, Chunk.class);

        VectorQueryConfig vectorQueryConfig =
                new VectorQueryConfig(10, 10, 10);

        WebappConfig webappConfig =
                new WebappConfig(chatModelType, vectorQueryConfig, openAiApiKey, symmetricSigningKey);

        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore, vectorStoreMemory, embeddingSpec);

        LocalServer localServer = new LocalServer();
        localServer.startServer(new WebappHandler(queryHandler), 80);

        System.out.println("http://localhost:80");
    }

}
