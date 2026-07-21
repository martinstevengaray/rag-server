package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.awsresources.Datastore;

import java.util.List;

public class SourceTransformerMain {

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        Datastore outputDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, outputBucket);
        SourceValidator sourceValidator = new SourceValidator();
        String sourceManifestId = null;
        String inputBucket = null;
        SourceTransformer sourceTransformer = null;
        IDatastore inputDataStore = null;
        Models.IngestionManifest ingestionManifest = null;
        List<String> errors = null;

        sourceManifestId = "portland-city-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/portland_city_code/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForPortland(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "oregon-state-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForOregon(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "web-catholic-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "new-american-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/new-american-bible/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);
    }

}
