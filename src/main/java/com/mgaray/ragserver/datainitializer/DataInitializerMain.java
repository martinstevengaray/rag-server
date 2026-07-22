package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.awsresources.Datastore;

import java.util.List;

public class DataInitializerMain {

    private static final String inputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
    private static final String outputBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        String ingestManifestId = portlandSourceManifestId;
        String inputSourceSubfolder = ingestManifestId;
        IDatastore inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        IDatastore outputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, outputBucket);
        DataInitializer dataInitializer = new DataInitializer(inputDataStore, outputDataStore);
        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(500, 0.5f),
                new EmbeddingSpec(Models.ModelType.BGE_SMALL_EN_V15_QUANTIZED));
        SourceCatalog sourceCatalog = inputDataStore.readObject(
                "/" + inputSourceSubfolder + "/sourceCatalog.json", SourceCatalog.class);
        List<String> errors = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);
        String ingestManifestLocation = Models.ingestManifestLocation(ingestManifestId);
        IngestionManifest ingestionManifest = outputDataStore.readObject(ingestManifestLocation, IngestionManifest.class);
        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() + ". errors: " + errors);
    }

}
