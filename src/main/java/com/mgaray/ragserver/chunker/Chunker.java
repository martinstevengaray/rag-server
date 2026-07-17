package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.BucketDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    record ChunkingSpec(int wordCount, float percentOverlap) {}

    private static final ChunkingSpec chunkingSpec = new ChunkingSpec(500, 0.5f);

    private final BucketDelegate bucketDelegate = new BucketDelegate();

    public Chunker() {
    }

    public Models.Chunks chunk(Models.SourceRecords sourceRecords) {
        List<Models.Chunk> chunks = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceRecords.sourceRecords()) {
            chunks.addAll(chunk(sourceRecord));
        }
        return new Models.Chunks(chunks);
    }

    private List<Models.Chunk> chunk(Models.SourceRecord sourceRecord) {
        String originalText = bucketDelegate.fetch(sourceRecord.textUrl());
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Models.Chunk> chunks = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunk = chunkedText.get(chunkIndex);
            chunks.add(new Models.Chunk(sourceRecord, chunkIndex, null, null)); //todo (..., float[] embedding, Resource chunkedTextUrl)

        }
        return chunks;
    }

    public List<String> chunk(String original, ChunkingSpec chunkingSpec) {
        List<String> chunks = new ArrayList<>();
        if (original == null || original.isBlank()) {
            return chunks;
        }
        String[] words = original.trim().split("\\s+");
        int chunkSize = chunkingSpec.wordCount();
        // step < chunkSize makes consecutive chunks overlap; clamp so we always advance
        int step = Math.max(1, Math.round(chunkSize * (1 - chunkingSpec.percentOverlap())));
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }

}
