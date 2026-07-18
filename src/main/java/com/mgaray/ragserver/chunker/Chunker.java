package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    private final DataFetcher dataFetcher;
    private final Embedder embedder;;
    private final boolean createVectorStore;

    public Chunker(DataFetcher dataFetcher) {
        this(dataFetcher, false);
    }

    private Chunker(DataFetcher dataFetcher, boolean createVectorStore) {
        this.createVectorStore = createVectorStore;
        this.dataFetcher = dataFetcher;
        this.embedder = new Embedder(Embedder.ModelType.DUMMY);
    }

    public Models.SourceManifest chunk(Models.SourceManifest sourceManifest,
                                       Models.ChunkingSpec chunkingSpec) {
        List<Models.SourceRecord> updateSourceRecords = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceManifest.sourceRecords()) {
            List<Models.Chunk> chunks = chunk(sourceManifest.id(), sourceRecord, chunkingSpec);
            Models.ChunkManifest chunkManifest = new Models.ChunkManifest(chunks, chunkingSpec);
            String chunkManifestLocation = "/" + sourceManifest.id() + "/sourceRecords/" + sourceRecord.id() + "/chunkManifest.json";
            dataFetcher.save(chunkManifestLocation, chunkManifest);
            updateSourceRecords.add(sourceRecord.withChunkManifestLocation(chunkManifestLocation));
        }
        return new Models.SourceManifest(sourceManifest.id(), updateSourceRecords);
    }

    private List<Models.Chunk> chunk(String sourceManifestId,
                                     Models.SourceRecord sourceRecord,
                                     Models.ChunkingSpec chunkingSpec) {
        String originalText = dataFetcher.fetch(sourceRecord.textLocation());
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Models.Chunk> chunks = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunkId = String.format("%03d", chunkIndex);  //the 3 should by dynamic todo!
            String chunkText = chunkedText.get(chunkIndex);
            String checkTextStorageLocation = "/" + sourceManifestId + "/sourceRecords/" + sourceRecord.id() + "/chunks/" + chunkId + ".txt";
            if (!dataFetcher.exists(checkTextStorageLocation)) {
                dataFetcher.save(checkTextStorageLocation, chunkText);
            }
            String embeddingStorageLocation = "/" + sourceManifestId + "/sourceRecords/" + sourceRecord.id() + "/embeddings/" + embedder.getModelName() + "-" + chunkId + ".bin";
            if (!dataFetcher.exists(embeddingStorageLocation)) {
                float[] chunkEmbedding = embedder.embed(chunkText).vector();
                dataFetcher.save(embeddingStorageLocation, chunkEmbedding);
            }
            chunks.add(new Models.Chunk(sourceRecord, chunkIndex, checkTextStorageLocation, embeddingStorageLocation));
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
