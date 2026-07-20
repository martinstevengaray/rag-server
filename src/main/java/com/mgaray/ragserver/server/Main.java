package com.mgaray.ragserver.server;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.rag.QueryHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "local-embedding-portland-city-code";
    private static final String openAiApiKey;// = System.getenv("OPEN_AI_API_KEY");

    static {
        openAiApiKey = readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh",
                "OPEN_AI_API_KEY");
    }

    // curl "http://localhost/mypath"
    // curl -X POST -H "Content-Type: application/json" -d '{"username": "Bob", "password": "bob-secret"}' http://localhost/somepath

    public static void main(String[] args) throws Exception {
        IDatastore dataStore = new Datastore(Datastore.Mode.ON_DISK, bucket);
        QueryHandler queryHandler = new QueryHandler(dataStore, openAiApiKey, sourceManifestId);
        JavaCoreServer javaCoreServer = new JavaCoreServer();
        javaCoreServer.startServer(new WebappHandler(queryHandler), 80);
    }


    private static String readKeyFromConfig(String filePath, String varName) {
        try {
            List<String> lines = Files.readAllLines(Path.of(filePath));
            for (String line : lines) {
                String trimmed = line.trim();
                // strip a leading "export "
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                // match "<varName>=..."
                String prefix = varName + "=";
                if (trimmed.startsWith(prefix)) {
                    String value = trimmed.substring(prefix.length()).trim();
                    // strip surrounding single or double quotes, if present
                    if (value.length() >= 2
                            && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                            && value.charAt(value.length() - 1) == value.charAt(0)) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
            throw new RuntimeException(varName + " not found in " + filePath);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read config file: " + filePath, e);
        }
    }

}
