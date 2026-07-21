package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models;

public class EmbeddMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        Datastore dataStore = new Datastore(Datastore.Mode.LOCAL_DISK, bucket);
        String sourceManifestLocation = Models.sourceManifestLocation(portlandSourceManifestId);
        Models.IngestionManifest ingestionManifest = dataStore.readObject(sourceManifestLocation, Models.IngestionManifest.class);
        Embedder embedder = new Embedder(dataStore);
        embedder.embed(ingestionManifest);
        System.out.println(JsonUtils.toJsonPretty(ingestionManifest));
    }

}
