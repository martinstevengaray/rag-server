package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.ModelType;

public class BootstapperMain {

    private static final String sourceBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
    private static final String outBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandIngestManifestId = "portland-city-code";
    private static final String portlandSourceCatalogLocation = "portland-city-code/sourceCatalog.json";
    private static final String oregonIngestManifestId = "oregon-state-code";
    private static final String websourceIngestManifestId = "web-catholic-bible";
    private static final String nabIngestManifestId = "new-american-bible";

    public static void main(String[] args) {
        int numberOfEmbeddingThreads = 10;
        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads);
        IDatastore sourceDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, sourceBucket);
        IDatastore outDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, outBucket);
        //IDatastore sourceDatastoreS3 = new Datastore(Datastore.Mode.S3, "mgaray-developer-temp-source");
        //IDatastore outDatastoreS3 =new Datastore(Datastore.Mode.S3, "mgaray-developer-temp")
        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(500, 0.5f),
                new EmbeddingSpec(ModelType.BGE_SMALL_EN_V15_QUANTIZED));

        Bootstrapper bootstrapper = new Bootstrapper(config, sourceDatastore, outDatastore);
        bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
    }

}
