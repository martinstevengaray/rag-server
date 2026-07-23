package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.Datastore;

import java.util.List;

public class SourceTransformerMain {

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        Datastore outputDatastore = new Datastore(Datastore.Mode.LOCAL_DISK, outputBucket);
        //Datastore outputDatastore = new Datastore(Datastore.Mode.S3, "rag-server-source");
        String sourceCatalogId = null;
        String inputBucket = null;
        SourceTransformer sourceTransformer = null;
        IDatastore inputDataStore = null;
        List<String> errors = null;

        sourceCatalogId = "portland-city-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/portland_city_code/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForPortland(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "oregon-state-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForOregon(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        if (true) return;

        sourceCatalogId = "web-catholic-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "new-american-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/new-american-bible/downloads-clean";
        inputDataStore = new Datastore(Datastore.Mode.LOCAL_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

}
