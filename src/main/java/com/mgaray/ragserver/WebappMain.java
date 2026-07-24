package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.WebappConfig;
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

public class WebappMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "portland-city-code";
    private static final String openAiApiKey;// = System.getenv("OPEN_AI_API_KEY");

    static {
        openAiApiKey = readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
    }

    public static void main(String[] args) throws Exception {
        //IDatastore datastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
//        IDatastore dataStoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        IDatastore dataStoreS3 = new Datastore(Datastore.Mode.S3, "rag-server-ingestion");
        //IDatastore datastore = new DatastoreCache(datastoreMemory, dataStoreDisk, dataStoreS3);

//        IVectorStore<Chunk> vectorStore = InMemoryVectorStore.load(dataStoreDisk, sourceManifestId, Chunk.class);


        IVectorStore<Chunk> vectorStoreS3 = new S3VectorStore<>("rag-server-vector", sourceManifestId, 1536, Chunk.class); //1536 todo

        WebappConfig webappConfig = new WebappConfig(OPEN_AI_GPT_4O_MINI, openAiApiKey, 10);

        QueryHandler queryHandler = new QueryHandler(webappConfig, dataStoreS3, vectorStoreS3, sourceManifestId);
        JavaCoreServer javaCoreServer = new JavaCoreServer();
        javaCoreServer.startServer(new WebappHandler(queryHandler), 80);
    }

    public static String readKeyFromConfig(String configFilename, String key) {
        try {
            List<String> lines = Files.readAllLines(Path.of(configFilename));
            for (String line : lines) {
                String prefix = "export " + key + "=";
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length() + 1, line.length() -1); //1 offsets for start and end quotes
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException(key +" not found in " + configFilename);
    }

}
