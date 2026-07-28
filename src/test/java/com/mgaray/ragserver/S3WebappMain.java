package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.TieredDatastore;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.VectorQueryConfig;
import com.mgaray.ragserver.Models.WebappConfig;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.ChatModelType;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.localrunutils.LocalServer;
import com.mgaray.ragserver.localrunutils.WebappHandler;
import com.mgaray.ragserver.server.QueryHandler;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.S3VectorStore;

public class S3WebappMain {

    private static final String ingestionManifestId = BootstrapperMain.portlandIngestManifestId;
    private static final ChatModelType chatModelType = ChatModelType.OPEN_AI_GPT_4O_MINI;

    public static void main(String[] args) throws Exception {
        String s3Bucket = BootstrapperMain.s3IngestionBucket;
        String s3VectorStoreBucket = BootstrapperMain.s3VectorStoreBucket;
        String openAiApiKey = SsmDelegate.getParameterFromLocalConfig("OPEN_AI_API_KEY");
        String symmetricSigningKey = SsmDelegate.getParameterFromLocalConfig("SYMMETRIC_SIGNING_KEY");

        IDatastore datastoreMemory = new InMemoryDatastore();
        IDatastore dataStoreS3 = new S3Datastore(s3Bucket);
        IDatastore datastore = new TieredDatastore(datastoreMemory, dataStoreS3);

        IngestionManifest ingestionManifest = datastore.readIngestionManifest(ingestionManifestId);

        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>(s3VectorStoreBucket, ingestionManifestId, Chunk.class);

        VectorQueryConfig vectorQueryConfig = new VectorQueryConfig(10, 10, 10);

        WebappConfig webappConfig = new WebappConfig(chatModelType, vectorQueryConfig, openAiApiKey, symmetricSigningKey);

        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore, vectorStoreS3, embeddingSpec);

        LocalServer localServer = new LocalServer();
        localServer.startServer(new WebappHandler(queryHandler), 80);
    }

}
