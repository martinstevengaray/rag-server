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
        Models.SourceManifest sourceManifest = dataFetcher.fetch(Models.sourceManifestLocation(portlandSourceManifestId), Models.SourceManifest.class);
        Chunker chunker = new Chunker(dataFetcher);
        Models.ChunkingSpec chunkingSpec = new Models.ChunkingSpec(500, 0.5f);
        System.out.println("start chunking");
        chunker.chunk(sourceManifest, chunkingSpec);
        System.out.println(JsonUtils.toJsonPretty(sourceManifest));
    }

}
