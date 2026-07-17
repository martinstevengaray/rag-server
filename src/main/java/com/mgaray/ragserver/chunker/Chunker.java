package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.BucketDelegate;

import java.util.ArrayList;
import java.util.List;

public class Chunker {

    private final BucketDelegate bucketDelegate = new BucketDelegate();

    public Chunker(String corpusBaseFolder) {
        //expected folder structure:
        //  title-XX.html
        //  title-XX.json
        //  title-XX.txt




    }

    public Models.Chunks chunk(Models.SourceRecords sourceRecords) {
        List<Models.Chunk> chunks = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceRecords.sourceRecords()) {
            chunks.add(chunk(sourceRecord));
        }
        return new Models.Chunks(chunks);
    }

    private Models.Chunk chunk(Models.SourceRecord sourceRecord) {
        String originalText = bucketDelegate.fetch(sourceRecord.textUrl());




        return null;
    }

    record ChunkingSpec(String wordCount, float percentOverlap) {}

    private List<String> chunk(String original, ChunkingSpec chunkingSpec) {


        return null;
    }


}
