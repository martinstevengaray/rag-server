package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;

public class VectorStoreMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        IDatastore dataStore = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        String sourceManifestLocation = Models.sourceManifestLocation(portlandSourceManifestId);
        Models.SourceManifest sourceManifest = dataStore.fetch(sourceManifestLocation, Models.SourceManifest.class);
        VectorStore vectorStore = new VectorStore(dataStore);
        vectorStore.load(sourceManifest);
    }

}
