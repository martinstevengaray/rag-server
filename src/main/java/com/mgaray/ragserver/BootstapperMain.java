package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.DatastoreCache;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Bootstrapper;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingModelType;

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
        String openAiApiKey = WebappMain.readKeyFromConfig(
                "/Users/turtlemccully/projects/rag-server/local/config.sh", "OPEN_AI_API_KEY");
        BootstrapperConfig config = new BootstrapperConfig(numberOfEmbeddingThreads, openAiApiKey);

        //IDatastore sourceDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, sourceBucket);
        IDatastore sourceDatastore = new Datastore(Datastore.Mode.S3, "rag-server-source");

        IDatastore outDatastoreMemory = new Datastore(Datastore.Mode.IN_MEMORY, null);
        IDatastore outDatastoreDisk = new Datastore(Datastore.Mode.LOCAL_DISK, outBucket);
        IDatastore outDatastoreS3 = new Datastore(Datastore.Mode.S3, "rag-server-ingestion");

        //IDatastore outDatastore = new DatastoreCache(outDatastoreMemory, outDatastoreDisk);
        IDatastore outDatastore = new DatastoreCache(outDatastoreMemory, outDatastoreDisk, outDatastoreS3);

        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(500, 0.5f),
                new EmbeddingSpec(EmbeddingModelType.OPEN_AI_TEXT_EMBEDDING_3_SMALL));

        Bootstrapper bootstrapper = new Bootstrapper(config, sourceDatastore, outDatastore);
        bootstrapper.bootstrap(portlandSourceCatalogLocation, portlandIngestManifestId, runDefinition);
    }

}
