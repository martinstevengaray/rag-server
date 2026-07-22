package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
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
        Models.RunDefinition runDefinition = new Models.RunDefinition(
                new Models.ChunkingSpec(500, 0.5f),
                new Models.EmbeddingSpec(Models.ModelType.DUMMY));
        Models.SourceCatalog sourceCatalog = inputDataStore.readObject(
                "/" + inputSourceSubfolder + "/sourceCatalog.json", Models.SourceCatalog.class);
        List<String> errors = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);
        String sourceManifestLocation = Models.sourceManifestLocation(ingestManifestId);
        Models.IngestionManifest ingestionManifest = outputDataStore.readObject(sourceManifestLocation, Models.IngestionManifest.class);
        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() + ". errors: " + errors);
    }

}
