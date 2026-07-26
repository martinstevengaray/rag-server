package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.VectorQueryConfig;
import com.mgaray.ragserver.rag.QueryHandler;
import com.mgaray.ragserver.server.JavaCoreServer;
import com.mgaray.ragserver.server.WebappHandler;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.InMemoryVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.mgaray.ragserver.common.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class WebappMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "portland-city-code";
    private static final String openAiApiKey;// = System.getenv("OPEN_AI_API_KEY");
    private static final String symmetricSigningKey;


    static {
        openAiApiKey = readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
        symmetricSigningKey= readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "SYMMETRIC_SIGNING_KEY");
    }

    public static void main(String[] args) throws Exception {

        IDatastore datastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore dataStoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        IDatastore dataStoreS3 = new Datastore(Datastore.Mode.S3, "rag-server-ingestion");
        IDatastore datastore = dataStoreS3;//new DatastoreCache(datastoreMemory, dataStoreDisk, dataStoreS3);

        String ingestionManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(ingestionManifestLocation, IngestionManifest.class);

        String inMemoryVectorStoreExportLocation = ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation();
        IVectorStore<Chunk> vectorStoreMemory = InMemoryVectorStore.load(dataStoreDisk, inMemoryVectorStoreExportLocation, Chunk.class);
        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>("rag-server-vector", sourceManifestId, Chunk.class);
        IVectorStore<Chunk> vectorStore = vectorStoreS3;

        VectorQueryConfig vectorQueryConfig = new VectorQueryConfig(10, 10, 10);

        WebappConfig webappConfig = new WebappConfig(OPEN_AI_GPT_4O_MINI, vectorQueryConfig, openAiApiKey, symmetricSigningKey);

        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore, vectorStore, embeddingSpec);

        JavaCoreServer javaCoreServer = new JavaCoreServer();
        javaCoreServer.startServer(new WebappHandler(queryHandler), 80);
    }

    public static String readKeyFromConfig(String configFilename, String key) {
        try {
            List<String> lines = Files.readAllLines(Path.of(configFilename));
            for (String line : lines) {
                String prefix = "export " + key + "=";
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).split("\"")[1];
                    //return line.substring(prefix.length() + 1, line.length() -1); //1 offsets for start and end quotes
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException(key +" not found in " + configFilename);
    }

}
