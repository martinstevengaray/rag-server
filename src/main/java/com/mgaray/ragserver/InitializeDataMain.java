package com.mgaray.ragserver;

import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.chunker.Chunker;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.sourcereader.SourceReader;

public class InitializeDataMain {


    private static final String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";

    public static void main(String[] args) {
        DataFetcher dataFetcher = new DataFetcher(DataFetcher.Mode.ON_DISK, "/Users/turtlemccully/projects/rag-server/local/s3bucket");
        SourceReader sourceReader = new SourceReader(dataFetcher);
        Chunker chunker = new Chunker(dataFetcher);

        Models.SourceManifest sourceManifest = sourceReader.sourceFolderForPortland("portland-city-code", inputPortland);
        Models.ChunkingSpec chunkingSpec = new Models.ChunkingSpec(500, 0.5f);

        System.out.println("start chunking");
        chunker.chunk(sourceManifest, chunkingSpec);

        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifest.id());
        dataFetcher.save(sourceManifestLocation, sourceManifest);

        System.out.println(JsonUtils.toJsonPretty(sourceManifest));

    }

}
