package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    private static final Models.ChunkingSpec defaulChunkingSpec = new Models.ChunkingSpec(500, 0.5f);

    private final DataFetcher dataFetcher = new DataFetcher();

    public Chunker() {
    }

    public Models.ChunkManifest chunk(Models.SourceManifest sourceManifest) {
        return chunk(sourceManifest, defaulChunkingSpec);
    }

    public Models.ChunkManifest chunk(Models.SourceManifest sourceManifest, Models.ChunkingSpec chunkingSpec) {
        List<Models.Chunk> chunks = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceManifest.sourceRecords()) {
            chunks.addAll(chunk(sourceRecord, chunkingSpec));
        }
        return new Models.ChunkManifest(chunks, chunkingSpec);
    }

    private List<Models.Chunk> chunk(Models.SourceRecord sourceRecord, Models.ChunkingSpec chunkingSpec) {
        String originalText = dataFetcher.fetchSourceRecordText(sourceRecord);
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Models.Chunk> chunks = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunkText = chunkedText.get(chunkIndex);
            Models.Chunk chunk = new Models.Chunk(
                    sourceRecord,
                    chunkIndex,
                    new Models.StorageLocation(null, null),
                    new Models.StorageLocation(null, null),
                    chunkText,
                    null);
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<String> chunk(String original, Models.ChunkingSpec chunkingSpec) {
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


    //  S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/chunkManifest.txt
    private String createChunkManifestLocation(String downloadsFolder, String sourceManifestId, String sourceRecordId) {
        return downloadsFolder + "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

}
