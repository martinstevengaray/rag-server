package com.mgaray.ragserver.sourcecatalogwriter;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;

import java.util.List;

public class SourceCatalogWriterMain {

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");
        String sourceCatalogId = null;
        String inputBucket = null;
        SourceCatalogWriter sourceCatalogWriter = null;
        IDatastore inputDatastore = null;
        List<String> errors = null;

        sourceCatalogId = "portland-city-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/portland_city_code/downloads-clean";
        inputDatastore = new LocalDiskDatastore(inputBucket);
        sourceCatalogWriter = new SourceCatalogWriter(inputDatastore, outputDatastore);
        errors = sourceCatalogWriter.sourceFolderForPortland(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "oregon-state-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        inputDatastore = new LocalDiskDatastore(inputBucket);
        sourceCatalogWriter = new SourceCatalogWriter(inputDatastore, outputDatastore);
        errors = sourceCatalogWriter.sourceFolderForOregon(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        if (true) return;

        sourceCatalogId = "web-catholic-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        inputDatastore = new LocalDiskDatastore(inputBucket);
        sourceCatalogWriter = new SourceCatalogWriter(inputDatastore, outputDatastore);
        errors = sourceCatalogWriter.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);

        sourceCatalogId = "new-american-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/new-american-bible/downloads-clean";
        inputDatastore = new LocalDiskDatastore(inputBucket);
        sourceCatalogWriter = new SourceCatalogWriter(inputDatastore, outputDatastore);
        errors = sourceCatalogWriter.sourceFolderForNabAndWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

}
