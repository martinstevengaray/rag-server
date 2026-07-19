package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.List;

public class ScoreTransformRunner {

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        DataFetcher outputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, outputBucket);
        SourceValidator sourceValidator = new SourceValidator();
        String sourceManifestId = null;
        String inputBucket = null;
        SourceTransformer sourceTransformer = null;
        DataFetcher inputDataFetcher = null;
        Models.SourceManifest sourceManifest = null;
        List<String> errors = null;

        sourceManifestId = "portland-city-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/portland_city_code/downloads-clean";
        inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataFetcher, outputDataFetcher);
        errors = sourceTransformer.sourceFolderForPortland(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "oregon-state-code";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataFetcher, outputDataFetcher);
        errors = sourceTransformer.sourceFolderForOregon(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "web-catholic-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataFetcher, outputDataFetcher);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);

        sourceManifestId = "new-american-bible";
        inputBucket = "/Users/turtlemccully/projects//rag-content-corpus-download/src/new-american-bible/downloads-clean";
        inputDataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, inputBucket);
        sourceTransformer = new SourceTransformer(inputDataFetcher, outputDataFetcher);
        errors = sourceTransformer.sourceFolderForNabAndWebc(sourceManifestId);
        System.out.println(sourceManifestId + " errors: " + errors);
    }

}
