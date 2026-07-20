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
        String sourceManifestId = portlandSourceManifestId;
        DataFetcher inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        DataFetcher outputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, outputBucket);
        DataInitializer dataInitializer = new DataInitializer(inputDataFetcher, outputDataFetcher);
        Models.RunDefinition runDefinition = new Models.RunDefinition(
                new Models.ChunkingSpec(500, 0.5f),
                new Models.EmbeddingSpec(Models.ModelType.BGE_SMALL_EN_V15_QUANTIZED));
        Models.SourceManifest inputSourceManifest = inputDataFetcher.fetch(
                "/" + sourceManifestId + "/sourceManifest.json", Models.SourceManifest.class);
        List<String> errors = dataInitializer.create(inputSourceManifest, runDefinition);
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        Models.SourceManifest sourceManifest = outputDataFetcher.fetch(sourceManifestLocation, Models.SourceManifest.class);
        System.out.println(sourceManifestId + " sourceRecords: " + sourceManifest.sourceRecords().size() + ". errors: " + errors);
    }

}
