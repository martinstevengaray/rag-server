package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.rag.QueryHandler;
import com.mgaray.ragserver.server.JavaCoreServer;
import com.mgaray.ragserver.server.WebappHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WebappMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "portland-city-code";
    private static final String openAiApiKey;// = System.getenv("OPEN_AI_API_KEY");

    static {
        openAiApiKey = readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
    }

    public static void main(String[] args) throws Exception {
        IDatastore datastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore dataStoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        IDatastore dataStoreS3 = new Datastore(Datastore.Mode.S3, "mgaray-developer-temp");
        IDatastore datastore = new DatastoreCache(datastoreMemory, dataStoreDisk, dataStoreS3);

        QueryHandler queryHandler = new QueryHandler(dataStoreDisk, openAiApiKey, sourceManifestId);
        JavaCoreServer javaCoreServer = new JavaCoreServer();
        javaCoreServer.startServer(new WebappHandler(queryHandler), 80);
    }

    private static String readKeyFromConfig(String configFilename, String key) {
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
