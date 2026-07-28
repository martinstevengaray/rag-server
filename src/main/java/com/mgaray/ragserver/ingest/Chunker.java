package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.ChunkManifest;
import com.mgaray.ragserver.Models.SourceRecordsDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Chunker {

    private final IDatastore datastore;

    public Chunker(IDatastore datastore) {
        this.datastore = datastore;
    }

    public void chunk(IngestionManifest ingestionManifest, SourceRecordsDocument sourceRecordsDocument) {
        ChunkingSpec chunkingSpec = ingestionManifest.runDefinition().chunkingSpec();
        for (SourceRecord sourceRecord : sourceRecordsDocument.sourceRecords()) {
            String chunkManifestLocation = sourceRecord.chunkManifestLocation();
            if (!datastore.exists(chunkManifestLocation)) {
                List<Chunk> chunks = chunk(ingestionManifest.id(), sourceRecord, chunkingSpec);
                ChunkManifest chunkManifest = new ChunkManifest(chunks);
                datastore.writeObject(chunkManifestLocation, chunkManifest);
            }
        }
    }

    private List<Chunk> chunk(String sourceManifestId,
                                     SourceRecord sourceRecord,
                                     ChunkingSpec chunkingSpec) {
        String originalText = datastore.readString(sourceRecord.textLocation());
        List<String> chunkedText = chunk(originalText, chunkingSpec);
        List<Chunk> chunks = new ArrayList<>();
        int digitCount = (int)Math.ceil(Math.log10(chunkedText.size() + 1));
        for (int chunkIndex = 0; chunkIndex < chunkedText.size(); chunkIndex++) {
            String chunkId = String.format("%0" + digitCount + "d", chunkIndex);
            String chunkText = chunkedText.get(chunkIndex);
            String chuckTextLocation = chunkTextLocation(sourceManifestId, sourceRecord.id(), chunkId);
            if (!datastore.exists(chuckTextLocation)) {
                datastore.writeString(chuckTextLocation, chunkText);
            }
            String embeddingLocation = embeddingLocation(sourceManifestId, sourceRecord.id(), chunkId);
            chunks.add(new Chunk(sourceRecord, chunkIndex, chuckTextLocation, embeddingLocation));
        }
        return chunks;
    }

    private List<String> chunk(String original, ChunkingSpec chunkingSpec) {
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

    private static String chunkTextLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunks/" + chunkId + ".txt";
    }

    private static String embeddingLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/embeddings/" + chunkId + ".bin";
    }

}
