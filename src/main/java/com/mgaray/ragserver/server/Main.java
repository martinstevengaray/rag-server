package com.mgaray.ragserver.server;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.rag.QueryHandler;

public class Main {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";
    private static final String sourceManifestId = "local-embedding-portland-city-code";

    // curl "http://localhost/mypath"
    // curl -X POST -H "Content-Type: application/json" -d '{"username": "Bob", "password": "bob-secret"}' http://localhost/somepath

    public static void main(String[] args) throws Exception {
        IDatastore dataStore = new Datastore(Datastore.Mode.ON_DISK, bucket);
        QueryHandler queryHandler = new QueryHandler(dataStore, sourceManifestId);
        JavaCoreServer javaCoreServer = new JavaCoreServer();
        javaCoreServer.startServer(new WebappHandler(queryHandler), 80);
    }


}
