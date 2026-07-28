package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.ChunkManifest;
import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorStoreLoaderTest {

    private static final String RUN_ID = "run-1";
    private static final VectorStoreSpec VECTOR_STORE_SPEC =
            new VectorStoreSpec(RUN_ID + "/vectorStore.json.gz", RUN_ID + "/s3VectorStore.json");

    /** Records what the loader asks of the store, without any embedding machinery. */
    private static class FakeVectorStore implements IVectorStore<Chunk> {
        private final Map<String, Chunk> chunksById = new LinkedHashMap<>();
        private final List<float[]> vectorsAdded = new ArrayList<>();
        private boolean resultsExist = false;
        private int writeResultsCount = 0;

        @Override
        public void add(float[] vector, Chunk chunk) {
            chunksById.put(chunk.id(), chunk);
            vectorsAdded.add(vector);
        }

        @Override
        public List<VectorMatch<Chunk>> get(float[] searchVector, int topK) {
            throw new UnsupportedOperationException("not used while loading");
        }

        @Override
        public Chunk get(String id) {
            return chunksById.get(id);
        }

        @Override
        public void initialize(EmbeddingSpec embeddingSpec) {}

        @Override
        public boolean resultsExist(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
            return resultsExist;
        }

        @Override
        public void writeResults(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
            writeResultsCount++;
        }

        @Override
        public boolean exists(Chunk chunk) {
            return chunksById.containsKey(chunk.id());
        }
    }

    private final IDatastore datastore = new InMemoryDatastore();
    private final FakeVectorStore vectorStore = new FakeVectorStore();
    private final VectorStoreLoader loader = new VectorStoreLoader(datastore, vectorStore);

    private static final IngestionManifest MANIFEST = new IngestionManifest(
            RUN_ID,
            new RunDefinition(new ChunkingSpec(4, 0.5f), new EmbeddingSpec(EmbeddingModelType.DUMMY)),
            RUN_ID + "/sourceRecordsDocument.json",
            VECTOR_STORE_SPEC);

    private static SourceRecord sourceRecord(String id) {
        return new SourceRecord(id, "https://example.com/" + id, "2026-01-01", "Title " + id,
                RUN_ID + "/sourceRecords/" + id + "/sourceRecord.txt",
                RUN_ID + "/sourceRecords/" + id + "/chunkManifest.json");
    }

    /** Writes a chunk manifest plus one embedding per chunk, and returns the source record. */
    private SourceRecord givenChunkedSource(String id, float[]... embeddings) {
        SourceRecord sourceRecord = sourceRecord(id);
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < embeddings.length; i++) {
            String embeddingLocation = RUN_ID + "/sourceRecords/" + id + "/embeddings/" + i + ".bin";
            chunks.add(new Chunk(sourceRecord, i, RUN_ID + "/sourceRecords/" + id + "/chunks/" + i + ".txt",
                    embeddingLocation));
            datastore.writeFloatArray(embeddingLocation, embeddings[i]);
        }
        datastore.writeObject(sourceRecord.chunkManifestLocation(), new ChunkManifest(chunks));
        return sourceRecord;
    }

    private static SourceRecordsDocument document(SourceRecord... sourceRecords) {
        return new SourceRecordsDocument(List.of(sourceRecords));
    }

    @Test
    void addsEveryChunkWithItsStoredEmbedding() {
        SourceRecord sourceRecord = givenChunkedSource("a", new float[]{1f, 0f}, new float[]{0f, 1f});

        loader.load(MANIFEST, document(sourceRecord));

        assertEquals(List.of("a:0", "a:1"), List.copyOf(vectorStore.chunksById.keySet()));
        assertArrayEquals(new float[]{1f, 0f}, vectorStore.vectorsAdded.get(0));
        assertArrayEquals(new float[]{0f, 1f}, vectorStore.vectorsAdded.get(1));
    }

    @Test
    void loadsChunksFromEverySourceRecord() {
        SourceRecord first = givenChunkedSource("a", new float[]{1f, 0f});
        SourceRecord second = givenChunkedSource("b", new float[]{0f, 1f});

        loader.load(MANIFEST, document(first, second));

        assertEquals(List.of("a:0", "b:0"), List.copyOf(vectorStore.chunksById.keySet()));
    }

    @Test
    void writesResultsOnceTheChunksAreLoaded() {
        SourceRecord sourceRecord = givenChunkedSource("a", new float[]{1f, 0f});

        loader.load(MANIFEST, document(sourceRecord));

        assertEquals(1, vectorStore.writeResultsCount);
    }

    @Test
    void skipsEverythingWhenResultsAlreadyExist() {
        SourceRecord sourceRecord = givenChunkedSource("a", new float[]{1f, 0f});
        vectorStore.resultsExist = true;

        loader.load(MANIFEST, document(sourceRecord));

        assertTrue(vectorStore.chunksById.isEmpty(), "nothing should be re-added");
        assertEquals(0, vectorStore.writeResultsCount, "and nothing should be rewritten");
    }

    @Test
    void doesNotReAddAChunkTheStoreAlreadyHas() {
        SourceRecord sourceRecord = givenChunkedSource("a", new float[]{1f, 0f}, new float[]{0f, 1f});
        vectorStore.add(new float[]{9f, 9f}, new Chunk(sourceRecord, 0, "ignored", "ignored"));
        vectorStore.vectorsAdded.clear();

        loader.load(MANIFEST, document(sourceRecord));

        assertEquals(1, vectorStore.vectorsAdded.size(), "only the missing chunk should be added");
        assertArrayEquals(new float[]{0f, 1f}, vectorStore.vectorsAdded.get(0));
    }

    @Test
    void handlesASourceRecordWithNoChunks() {
        SourceRecord sourceRecord = givenChunkedSource("a");

        loader.load(MANIFEST, document(sourceRecord));

        assertTrue(vectorStore.chunksById.isEmpty());
        assertEquals(1, vectorStore.writeResultsCount, "an empty run still publishes its (empty) results");
    }

    @Test
    void handlesAnEmptyDocument() {
        loader.load(MANIFEST, document());

        assertTrue(vectorStore.chunksById.isEmpty());
        assertEquals(1, vectorStore.writeResultsCount);
    }

}
