package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.List;

public class DataInitializerMain {

    private static final String inputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
    private static final String outputBucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        DataFetcher inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        DataFetcher outputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, outputBucket);
        DataInitializer dataInitializer = new DataInitializer(inputDataFetcher, outputDataFetcher);
        List<String> errors = dataInitializer.create(portlandSourceManifestId);
        Models.SourceManifest sourceManifest = outputDataFetcher.fetch(Models.sourceManifestLocation(portlandSourceManifestId), Models.SourceManifest.class);
        System.out.println(portlandSourceManifestId + " sourceRecords: " + sourceManifest.sourceRecords().size() + ". errors: " + errors);
    }

}
