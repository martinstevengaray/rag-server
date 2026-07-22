package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.datainitializer.Bootstrapper;

public class BootstapperMain {

    private static final String sourceBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
    private static final String outBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandIngestManifestId = "portland-city-code";
    private static final String portlandSourceCatalogLocation = "/portland-city-code/sourceCatalog.json";
    private static final String oregonIngestManifestId = "oregon-state-code";
    private static final String websourceIngestManifestId = "web-catholic-bible";
    private static final String nabIngestManifestId = "new-american-bible";

    public static void main(String[] args) {
        Bootstrapper bootstrapper = new Bootstrapper(new Datastore(Datastore.Mode.LOCAL_DISK, sourceBucket),
                new Datastore(Datastore.Mode.LOCAL_DISK, outBucket));
        bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId);
    }

}
