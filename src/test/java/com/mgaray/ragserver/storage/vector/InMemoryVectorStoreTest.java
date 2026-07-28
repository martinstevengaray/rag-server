package com.mgaray.ragserver.storage.vector;

import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    private static final String EXPORT_LOCATION = "run-1/vectorStore.json.gz";
    private static final VectorStoreSpec VECTOR_STORE_SPEC =
            new VectorStoreSpec(EXPORT_LOCATION, "run-1/s3VectorStore.json");

    private final IDatastore datastore = new InMemoryDatastore();
    private final InMemoryVectorStore<Chunk> vectorStore = new InMemoryVectorStore<>(Chunk.class);

    private static SourceRecord sourceRecord(String id) {
        return new SourceRecord(id, "https://example.com/" + id, "2026-01-01", "Title " + id,
                "run-1/sourceRecords/" + id + "/sourceRecord.txt",
                "run-1/sourceRecords/" + id + "/chunkManifest.json");
    }

    private static Chunk chunk(String sourceRecordId, int index) {
        return new Chunk(sourceRecord(sourceRecordId), index,
                "run-1/sourceRecords/" + sourceRecordId + "/chunks/" + index + ".txt",
                "run-1/sourceRecords/" + sourceRecordId + "/embeddings/" + index + ".bin");
    }

    private static List<String> idsOf(List<VectorMatch<Chunk>> matches) {
        return matches.stream().map(match -> match.record().id()).collect(Collectors.toList());
    }

    @Test
    void findsTheNearestVectorFirst() {
        vectorStore.add(new float[]{1f, 0f, 0f}, chunk("a", 0));
        vectorStore.add(new float[]{0f, 1f, 0f}, chunk("b", 0));
        vectorStore.add(new float[]{0f, 0f, 1f}, chunk("c", 0));

        assertEquals(List.of("b:0"), idsOf(vectorStore.get(new float[]{0f, 1f, 0f}, 1)));
    }

    @Test
    void ranksMatchesByDescendingScore() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.add(new float[]{0.9f, 0.1f}, chunk("b", 0));
        vectorStore.add(new float[]{0f, 1f}, chunk("c", 0));

        List<VectorMatch<Chunk>> matches = vectorStore.get(new float[]{1f, 0f}, 3);

        assertEquals(List.of("a:0", "b:0", "c:0"), idsOf(matches));
        assertTrue(matches.get(0).matchScore() >= matches.get(1).matchScore());
        assertTrue(matches.get(1).matchScore() >= matches.get(2).matchScore());
    }

    @Test
    void honoursTopK() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.add(new float[]{0f, 1f}, chunk("b", 0));
        vectorStore.add(new float[]{1f, 1f}, chunk("c", 0));

        assertEquals(2, vectorStore.get(new float[]{1f, 0f}, 2).size());
    }

    @Test
    void returnsEverythingWhenTopKExceedsTheStoreSize() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));

        assertEquals(1, vectorStore.get(new float[]{1f, 0f}, 10).size());
    }

    @Test
    void searchingAnEmptyStoreReturnsNoMatches() {
        assertEquals(List.of(), vectorStore.get(new float[]{1f, 0f}, 5));
    }

    @Test
    void matchesCarryTheFullChunkNotJustItsId() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 3));

        Chunk match = vectorStore.get(new float[]{1f, 0f}, 1).get(0).record();

        assertEquals("a:3", match.id());
        assertEquals(3, match.index());
        assertEquals("https://example.com/a", match.sourceRecord().sourceUrl());
        assertEquals("run-1/sourceRecords/a/chunks/3.txt", match.textLocation());
    }

    @Test
    void looksUpAChunkById() {
        Chunk chunk = chunk("a", 0);
        vectorStore.add(new float[]{1f, 0f}, chunk);

        assertEquals(chunk, vectorStore.get("a:0"));
    }

    @Test
    void lookupReturnsNullForAnUnknownId() {
        assertNull(vectorStore.get("never:0"), "an id hallucinated into session state must not blow up the lookup");
    }

    @Test
    void existsTracksWhatHasBeenAdded() {
        Chunk chunk = chunk("a", 0);

        assertFalse(vectorStore.exists(chunk));

        vectorStore.add(new float[]{1f, 0f}, chunk);

        assertTrue(vectorStore.exists(chunk));
    }

    @Test
    void addingTheSameIdTwiceReplacesRatherThanDuplicates() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.add(new float[]{0f, 1f}, chunk("a", 0));

        List<VectorMatch<Chunk>> matches = vectorStore.get(new float[]{0f, 1f}, 10);

        assertEquals(List.of("a:0"), idsOf(matches), "re-adding an id should not leave a stale duplicate");
    }

    @Test
    void resultsExistReflectsTheExportLocation() {
        assertFalse(vectorStore.resultsExist(datastore, VECTOR_STORE_SPEC));

        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        assertTrue(vectorStore.resultsExist(datastore, VECTOR_STORE_SPEC));
    }

    @Test
    void writeResultsStoresAGzippedExport() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        byte[] stored = datastore.read(EXPORT_LOCATION);
        assertEquals((byte) 0x1f, stored[0]);
        assertEquals((byte) 0x8b, stored[1]);
    }

    @Test
    void loadRestoresSearchableVectors() {
        vectorStore.add(new float[]{1f, 0f, 0f}, chunk("a", 0));
        vectorStore.add(new float[]{0f, 1f, 0f}, chunk("b", 0));
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> loaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);

        assertEquals(List.of("b:0"), idsOf(loaded.get(new float[]{0f, 1f, 0f}, 1)));
    }

    @Test
    void loadRebuildsTheIdLookup() {
        Chunk chunk = chunk("a", 2);
        vectorStore.add(new float[]{1f, 0f}, chunk);
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> loaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);

        // the lookup is what QueryHandler uses to re-hydrate chunks cited in an earlier turn
        assertEquals(chunk, loaded.get("a:2"));
        assertTrue(loaded.exists(chunk));
    }

    @Test
    void loadRoundTripsEveryChunk() {
        for (int i = 0; i < 5; i++) {
            vectorStore.add(new float[]{i, 1f}, chunk("a", i));
        }
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> loaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);

        for (int i = 0; i < 5; i++) {
            assertEquals(chunk("a", i), loaded.get("a:" + i), "chunk a:" + i + " should survive the round trip");
        }
    }

    @Test
    void aLoadedStoreCanBeAddedToAndReexported() {
        vectorStore.add(new float[]{1f, 0f}, chunk("a", 0));
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> loaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);
        loaded.add(new float[]{0f, 1f}, chunk("b", 0));
        loaded.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> reloaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);
        assertEquals(chunk("a", 0), reloaded.get("a:0"));
        assertEquals(chunk("b", 0), reloaded.get("b:0"));
    }

    @Test
    void loadHandlesAnEmptyExport() {
        vectorStore.writeResults(datastore, VECTOR_STORE_SPEC);

        InMemoryVectorStore<Chunk> loaded = InMemoryVectorStore.load(datastore, EXPORT_LOCATION, Chunk.class);

        assertEquals(List.of(), loaded.get(new float[]{1f, 0f}, 5));
        assertNull(loaded.get("a:0"));
    }

}
