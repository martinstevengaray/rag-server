package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models;

public class ChunkerMain {

    private static final String bucket = "/Users/turtlemccully/projects/rag-server/local/s3bucket";

    private static final String portlandSourceManifestId = "portland-city-code";
    private static final String oregonSourceManifestId = "oregon-state-code";
    private static final String websourceManifestId = "web-catholic-bible";
    private static final String nabManifestId = "new-american-bible";

    public static void main(String[] args) {
        DataFetcher dataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, bucket);
        String sourceManifestLocation = Models.sourceManifestLocation(portlandSourceManifestId);
        Models.SourceManifest sourceManifest = dataFetcher.fetch(sourceManifestLocation, Models.SourceManifest.class);
        Chunker chunker = new Chunker(dataFetcher);
        System.out.println("start chunking");
        chunker.chunk(sourceManifest);
        System.out.println(JsonUtils.toJsonPretty(sourceManifest));
    }

}
