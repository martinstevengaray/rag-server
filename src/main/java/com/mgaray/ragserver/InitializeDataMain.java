package com.mgaray.ragserver;

import com.mgaray.ragserver.chunker.Chunker;
import com.mgaray.ragserver.sourcereader.SourceReader;

public class InitializeDataMain {


    private static final String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";

    public static void main(String[] args) {
        SourceReader sourceReader = new SourceReader();
        Chunker chunker = new Chunker();

        Models.SourceManifest sourceManifest = sourceReader.sourceFolderForPortland("developer-wip", inputPortland);
        Models.ChunkingSpec chunkingSpec = new Models.ChunkingSpec(500, 0.5f);

        System.out.println("start chunking");
        Models.ChunkManifest chunkManifest = chunker.chunk(sourceManifest, chunkingSpec, Chunker.Mode.IN_MEMORY, null);
//        System.out.println(chunkManifest);

    }
}
