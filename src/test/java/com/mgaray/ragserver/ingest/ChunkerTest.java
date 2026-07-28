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
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerTest {

    private static final String RUN_ID = "run-1";
    private static final String TEXT_LOCATION = "run-1/sourceRecords/src-a/sourceRecord.txt";
    private static final String CHUNK_MANIFEST_LOCATION = "run-1/sourceRecords/src-a/chunkManifest.json";

    private final IDatastore datastore = new InMemoryDatastore();
    private final Chunker chunker = new Chunker(datastore);

    private static final SourceRecord SOURCE_RECORD = new SourceRecord(
            "src-a", "https://example.com/a", "2026-01-01", "Title A", TEXT_LOCATION, CHUNK_MANIFEST_LOCATION);

    private static IngestionManifest manifest(int wordCount, float percentOverlap) {
        return new IngestionManifest(
                RUN_ID,
                new RunDefinition(new ChunkingSpec(wordCount, percentOverlap),
                                  new EmbeddingSpec(EmbeddingModelType.DUMMY)),
                RUN_ID + "/sourceRecordsDocument.json",
                new VectorStoreSpec(RUN_ID + "/vectorStore.json.gz", RUN_ID + "/s3VectorStore.json"));
    }

    private static final SourceRecordsDocument DOCUMENT = new SourceRecordsDocument(List.of(SOURCE_RECORD));

    /** Runs the chunker over {@code text} and returns the persisted manifest. */
    private ChunkManifest chunk(String text, int wordCount, float percentOverlap) {
        datastore.writeString(TEXT_LOCATION, text);
        chunker.chunk(manifest(wordCount, percentOverlap), DOCUMENT);
        return datastore.readObject(CHUNK_MANIFEST_LOCATION, ChunkManifest.class);
    }

    private List<String> chunkTexts(ChunkManifest chunkManifest) {
        return chunkManifest.chunks().stream()
                .map(chunk -> datastore.readString(chunk.textLocation()))
                .collect(Collectors.toList());
    }

    private static String words(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }

    @Test
    void splitsIntoOverlappingChunks() {
        // 10 words, size 4, 50% overlap -> step 2
        ChunkManifest chunkManifest = chunk(words(10), 4, 0.5f);

        assertEquals(List.of("w1 w2 w3 w4", "w3 w4 w5 w6", "w5 w6 w7 w8", "w7 w8 w9 w10"), chunkTexts(chunkManifest));
    }

    @Test
    void splitsIntoAdjacentChunksWhenOverlapIsZero() {
        ChunkManifest chunkManifest = chunk(words(6), 2, 0.0f);

        assertEquals(List.of("w1 w2", "w3 w4", "w5 w6"), chunkTexts(chunkManifest));
    }

    @Test
    void theFinalChunkIsShortWhenWordsDoNotDivideEvenly() {
        ChunkManifest chunkManifest = chunk(words(7), 3, 0.0f);

        assertEquals(List.of("w1 w2 w3", "w4 w5 w6", "w7"), chunkTexts(chunkManifest));
    }

    @Test
    void textShorterThanTheChunkSizeBecomesOneChunk() {
        ChunkManifest chunkManifest = chunk(words(3), 100, 0.5f);

        assertEquals(List.of("w1 w2 w3"), chunkTexts(chunkManifest));
    }

    @Test
    void fullOverlapIsClampedSoTheWindowStillAdvances() {
        // 100% overlap would mean step 0; the clamp forces step 1 rather than looping forever
        ChunkManifest chunkManifest = chunk(words(5), 3, 1.0f);

        assertEquals(List.of("w1 w2 w3", "w2 w3 w4", "w3 w4 w5"), chunkTexts(chunkManifest));
    }

    @Test
    void collapsesRunsOfWhitespaceAndTrimsEdges() {
        ChunkManifest chunkManifest = chunk("  w1\n\n w2\tw3   w4  ", 2, 0.0f);

        assertEquals(List.of("w1 w2", "w3 w4"), chunkTexts(chunkManifest));
    }

    @Test
    void emptyTextProducesNoChunksButStillWritesAManifest() {
        ChunkManifest chunkManifest = chunk("", 4, 0.5f);

        assertEquals(List.of(), chunkManifest.chunks());
        assertTrue(datastore.exists(CHUNK_MANIFEST_LOCATION));
    }

    @Test
    void blankTextProducesNoChunks() {
        ChunkManifest chunkManifest = chunk("   \n\t  ", 4, 0.5f);

        assertEquals(List.of(), chunkManifest.chunks());
    }

    @Test
    void chunkIndexesAreSequentialFromZero() {
        ChunkManifest chunkManifest = chunk(words(6), 2, 0.0f);

        assertEquals(List.of(0, 1, 2), chunkManifest.chunks().stream().map(Chunk::index).collect(Collectors.toList()));
    }

    @Test
    void chunkIdCombinesSourceRecordIdAndIndex() {
        ChunkManifest chunkManifest = chunk(words(4), 2, 0.0f);

        assertEquals(List.of("src-a:0", "src-a:1"),
                chunkManifest.chunks().stream().map(Chunk::id).collect(Collectors.toList()));
    }

    @Test
    void chunkLocationsFollowTheRunAndSourceRecordConvention() {
        ChunkManifest chunkManifest = chunk(words(4), 2, 0.0f);

        Chunk first = chunkManifest.chunks().get(0);
        assertEquals("run-1/sourceRecords/src-a/chunks/0.txt", first.textLocation());
        assertEquals("run-1/sourceRecords/src-a/embeddings/0.bin", first.embeddingLocation());
    }

    @Test
    void chunkFileNamesAreZeroPaddedOnceThereAreTenOrMoreChunks() {
        ChunkManifest chunkManifest = chunk(words(12), 1, 0.0f);

        assertEquals(12, chunkManifest.chunks().size());
        assertEquals("run-1/sourceRecords/src-a/chunks/00.txt", chunkManifest.chunks().get(0).textLocation());
        assertEquals("run-1/sourceRecords/src-a/chunks/11.txt", chunkManifest.chunks().get(11).textLocation());
    }

    @Test
    void chunkFileNamesAreUnpaddedBelowTenChunks() {
        ChunkManifest chunkManifest = chunk(words(9), 1, 0.0f);

        assertEquals(9, chunkManifest.chunks().size());
        assertEquals("run-1/sourceRecords/src-a/chunks/0.txt", chunkManifest.chunks().get(0).textLocation());
        assertEquals("run-1/sourceRecords/src-a/chunks/8.txt", chunkManifest.chunks().get(8).textLocation());
    }

    @Test
    void everyChunkCarriesItsSourceRecord() {
        ChunkManifest chunkManifest = chunk(words(4), 2, 0.0f);

        for (Chunk chunk : chunkManifest.chunks()) {
            assertEquals(SOURCE_RECORD, chunk.sourceRecord());
        }
    }

    @Test
    void embeddingsAreNotWrittenByChunking() {
        ChunkManifest chunkManifest = chunk(words(4), 2, 0.0f);

        for (Chunk chunk : chunkManifest.chunks()) {
            assertFalse(datastore.exists(chunk.embeddingLocation()),
                    "chunking only reserves the embedding location; Embedder fills it");
        }
    }

    @Test
    void rerunIsASkipWhenTheChunkManifestAlreadyExists() {
        datastore.writeString(TEXT_LOCATION, words(10));
        datastore.writeObject(CHUNK_MANIFEST_LOCATION, new ChunkManifest(List.of()));

        chunker.chunk(manifest(4, 0.5f), DOCUMENT);

        assertEquals(List.of(), datastore.readObject(CHUNK_MANIFEST_LOCATION, ChunkManifest.class).chunks());
        assertFalse(datastore.exists("run-1/sourceRecords/src-a/chunks/0.txt"), "no chunk text should be written");
    }

    @Test
    void existingChunkTextIsLeftInPlace() {
        // chunk text writes are skipped when the location already exists, so a re-chunk with a
        // different spec reuses whatever text is already there rather than overwriting it
        datastore.writeString("run-1/sourceRecords/src-a/chunks/0.txt", "text from an earlier run");

        ChunkManifest chunkManifest = chunk(words(4), 2, 0.0f);

        assertEquals("text from an earlier run", datastore.readString(chunkManifest.chunks().get(0).textLocation()));
        assertEquals("w3 w4", datastore.readString(chunkManifest.chunks().get(1).textLocation()));
    }

    @Test
    void chunksEverySourceRecordInTheDocument() {
        SourceRecord second = new SourceRecord("src-b", "https://example.com/b", "2026-01-01", "Title B",
                "run-1/sourceRecords/src-b/sourceRecord.txt", "run-1/sourceRecords/src-b/chunkManifest.json");
        datastore.writeString(TEXT_LOCATION, words(4));
        datastore.writeString(second.textLocation(), words(2));

        chunker.chunk(manifest(2, 0.0f), new SourceRecordsDocument(List.of(SOURCE_RECORD, second)));

        assertEquals(2, datastore.readObject(CHUNK_MANIFEST_LOCATION, ChunkManifest.class).chunks().size());
        assertEquals(1, datastore.readObject(second.chunkManifestLocation(), ChunkManifest.class).chunks().size());
    }

    @Test
    void chunkManifestSurvivesAJsonRoundTrip() {
        ChunkManifest written = chunk(words(10), 4, 0.5f);

        // the manifest is the hand-off to Embedder/VectorStoreLoader, so it must deserialize intact
        assertEquals(4, written.chunks().size());
        assertEquals("src-a:3", written.chunks().get(3).id());
        assertEquals("Title A", written.chunks().get(3).sourceRecord().title());
    }

}
