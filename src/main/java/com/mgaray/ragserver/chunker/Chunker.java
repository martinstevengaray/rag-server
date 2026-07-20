package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    private final IDatastore dataStore;

    public Chunker(IDatastore dataStore) {
        this.dataStore = dataStore;
    }

    public void chunk(Models.SourceManifest sourceManifest) {
        Models.ChunkingSpec chunkingSpec = sourceManifest.runDefinition().chunkingSpec();
        for (Models.SourceRecord sourceRecord : sourceManifest.sourceRecords()) {
            String chunkManifestLocation = sourceRecord.chunkManifestLocation();
            if (!dataStore.exists(chunkManifestLocation)) {
                List<Models.Chunk> chunks = chunk(sourceManifest.id(), sourceRecord, chunkingSpec);
                Models.ChunkManifest chunkManifest = new Models.ChunkManifest(chunks);
                dataStore.save(chunkManifestLocation, chunkManifest);
            }
        }
    }

    private List<Models.Chunk> chunk(String sourceManifestId,
                                     Models.SourceRecord sourceRecord,
                                     Models.ChunkingSpec chunkingSpec) {
        String originalText = dataStore.fetch(sourceRecord.textLocation());
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Models.Chunk> chunks = new ArrayList<>();
        int digitCount = (int)Math.ceil(Math.log10(chunkedText.size() + 1));
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunkId = String.format("%0" + digitCount + "d", chunkIndex);
            String chunkText = chunkedText.get(chunkIndex);
            String chuckTextLocation = Models.chunkTextLocation(sourceManifestId, sourceRecord.id(), chunkId);
            if (!dataStore.exists(chuckTextLocation)) {
                dataStore.save(chuckTextLocation, chunkText);
            }
            String embeddingLocation = Models.embeddingLocation(sourceManifestId, sourceRecord.id(), chunkId);
            chunks.add(new Models.Chunk(sourceRecord, chunkIndex, chuckTextLocation, embeddingLocation));
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

}
