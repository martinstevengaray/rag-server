package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.VectorQueryConfig;
import com.mgaray.ragserver.localserver.LocalServer;
import com.mgaray.ragserver.server.QueryHandler;
import com.mgaray.ragserver.localserver.WebappHandler;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;

import static com.mgaray.ragserver.common.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class WebappMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "portland-city-code";
    private static final String openAiApiKey;// = System.getenv("OPEN_AI_API_KEY");
    private static final String symmetricSigningKey;


    static {
        openAiApiKey = BootstapperMain.readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
        symmetricSigningKey= BootstapperMain.readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "SYMMETRIC_SIGNING_KEY");
    }

    public static void main(String[] args) throws Exception {

        IDatastore datastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore dataStoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        IDatastore dataStoreS3 = new Datastore(Datastore.Mode.S3, "rag-server-ingestion");
        IDatastore datastore = dataStoreDisk;//new DatastoreCache(datastoreMemory, dataStoreDisk, dataStoreS3);

        String ingestionManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(ingestionManifestLocation, IngestionManifest.class);

        String inMemoryVectorStoreExportLocation = ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation();
        IVectorStore<Chunk> vectorStoreMemory = InMemoryVectorStore.load(dataStoreDisk, inMemoryVectorStoreExportLocation, Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>("rag-server-vector", sourceManifestId, Chunk.class);
        IVectorStore<Chunk> vectorStore = vectorStoreMemory;

        VectorQueryConfig vectorQueryConfig = new VectorQueryConfig(10, 10, 10);

        WebappConfig webappConfig = new WebappConfig(OPEN_AI_GPT_4O_MINI, vectorQueryConfig, openAiApiKey, symmetricSigningKey);

        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore, vectorStore, embeddingSpec);

        LocalServer localServer = new LocalServer();
        localServer.startServer(new WebappHandler(queryHandler), 80);
    }

}
