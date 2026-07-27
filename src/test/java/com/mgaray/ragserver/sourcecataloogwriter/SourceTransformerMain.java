package com.mgaray.ragserver.sourcecataloogwriter;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.LocalDiskDatastore;
import com.mgaray.ragserver.awsresources.S3Datastore;

import java.util.List;

public class SourceTransformerMain {

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");
        String sourceCatalogId = null;
        String inputBucket = null;
        SourceTransformer sourceTransformer = null;
        IDatastore inputDataStore = null;
        List<String> errors = null;

        sourceCatalogId = "portland-city-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/portland_city_code/downloads-clean";
        inputDataStore = new LocalDiskDatastore(inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForPortland(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "oregon-state-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        inputDataStore = new LocalDiskDatastore(inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForOregon(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        if (true) return;

        sourceCatalogId = "web-catholic-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        inputDataStore = new LocalDiskDatastore(inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "new-american-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/new-american-bible/downloads-clean";
        inputDataStore = new LocalDiskDatastore(inputBucket);
        sourceTransformer = new SourceTransformer(inputDataStore, outputDatastore);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

}
