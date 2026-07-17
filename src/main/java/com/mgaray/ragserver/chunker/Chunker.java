package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    public enum Mode {IN_MEMORY, ON_DISK, ON_S3}

    private final DataFetcher dataFetcher;
    private final Embedder embedder;;
    private final Mode mode;
    private final String bucket;

    public Chunker(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
        this.dataFetcher = new DataFetcher();
        this.embedder = new Embedder(Embedder.ModelType.LOCAL);
    }

    public Models.ChunkManifest chunk(Models.SourceManifest sourceManifest,
                                      Models.ChunkingSpec chunkingSpec) {
        List<Models.Chunk> chunks = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceManifest.sourceRecords()) {
            chunks.addAll(chunk(sourceRecord, chunkingSpec));
        }
        return new Models.ChunkManifest(chunks, chunkingSpec);
    }

    private List<Models.Chunk> chunk(Models.SourceRecord sourceRecord,
                                     Models.ChunkingSpec chunkingSpec) {
        String originalText = dataFetcher.fetchSourceRecordText(sourceRecord);
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Models.Chunk> chunks = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunkText = chunkedText.get(chunkIndex);
            float[] chunkEmbedding = embedder.embed(chunkText).vector();
            chunks.add(createChunk(sourceRecord, chunkIndex, chunkText, chunkEmbedding));
        }
        return chunks;
    }

    Models.Chunk createChunk(Models.SourceRecord sourceRecord,
                             Integer chunkIndex,
                             String chunkText,
                             float[] chunkEmbedding) {
        switch (mode) {
            case IN_MEMORY:
                return new Models.Chunk(
                        sourceRecord,
                        chunkIndex,
                        new Models.StorageLocation(null, null),
                        new Models.StorageLocation(null, null),
                        chunkText,
                        chunkEmbedding);
            case ON_DISK: //todo
            case ON_S3: //todo
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
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
