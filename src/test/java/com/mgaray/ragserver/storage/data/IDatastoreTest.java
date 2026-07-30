package com.mgaray.ragserver.storage.data;

import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.util.GzipUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the default convenience methods on {@link IDatastore} through {@link InMemoryDatastore}. */
class IDatastoreTest {

    private final IDatastore datastore = new InMemoryDatastore();

    private static IngestionManifest manifest(String id) {
        return new IngestionManifest(
                id,
                new RunDefinition(new ChunkingSpec(250, 0.25f),
                                  new EmbeddingSpec(EmbeddingModelType.DUMMY)),
                id + "/sourceRecordsDocument.json",
                new VectorStoreSpec(id + "/vectorStore.json.gz", id + "/s3VectorStore.json"));
    }

    @Test
    void readReturnsNullForAnUnknownLocation() {
        assertNull(datastore.read("nothing/here"));
    }

    @Test
    void existsTracksWrites() {
        assertFalse(datastore.exists("a/b.txt"));

        datastore.writeString("a/b.txt", "content");

        assertTrue(datastore.exists("a/b.txt"));
    }

    @Test
    void writeOverwritesAnExistingLocation() {
        datastore.writeString("a/b.txt", "first");
        datastore.writeString("a/b.txt", "second");

        assertEquals("second", datastore.readString("a/b.txt"));
    }

    @Test
    void roundTripsAString() {
        datastore.writeString("a/b.txt", "the quick brown fox");

        assertEquals("the quick brown fox", datastore.readString("a/b.txt"));
    }

    @Test
    void roundTripsMultiByteUtf8AsUtf8Bytes() {
        String content = "café § 33.110";
        datastore.writeString("a/b.txt", content);

        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), datastore.read("a/b.txt"));
        assertEquals(content, datastore.readString("a/b.txt"));
    }

    @Test
    void roundTripsAnObjectAsPrettyJson() {
        ChunkingSpec spec = new ChunkingSpec(250, 0.25f);
        datastore.writeObject("spec.json", spec);

        assertTrue(datastore.readString("spec.json").contains("\n"), "writeObject should store pretty-printed json");
        assertEquals(spec, datastore.readObject("spec.json", ChunkingSpec.class));
    }

    @Test
    void roundTripsAFloatArrayAsLittleEndian() {
        float[] embedding = {1.5f, -2.25f, 0.0f, 3.125e10f};
        datastore.writeFloatArray("e.bin", embedding);

        assertEquals(embedding.length * Float.BYTES, datastore.read("e.bin").length);
        assertArrayEquals(embedding, datastore.readFloatArray("e.bin"));
    }

    @Test
    void roundTripsAnEmptyFloatArray() {
        datastore.writeFloatArray("e.bin", new float[0]);

        assertArrayEquals(new float[0], datastore.readFloatArray("e.bin"));
    }

    @Test
    void readFloatArrayRejectsAByteCountThatIsNotAMultipleOfFour() {
        datastore.write("e.bin", new byte[]{1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> datastore.readFloatArray("e.bin"));
    }

    @Test
    void roundTripsGzipBytes() {
        byte[] content = "compress me".getBytes(StandardCharsets.UTF_8);
        datastore.writeGzip("a/b.gz", content);

        assertArrayEquals(content, datastore.readGzip("a/b.gz"));
    }

    @Test
    void roundTripsGzipString() {
        datastore.writeGzipString("a/b.gz", "compress me");

        assertEquals("compress me", datastore.readGzipString("a/b.gz"));
    }

    @Test
    void roundTripsGzipObject() {
        ChunkingSpec spec = new ChunkingSpec(100, 0.5f);
        datastore.writeGzipObject("spec.json.gz", spec);

        assertEquals(spec, datastore.readGzipObject("spec.json.gz", ChunkingSpec.class));
    }

    @Test
    void gzipWritesAreActuallyCompressedOnTheWire() {
        datastore.writeGzipString("a/b.gz", "compress me");

        byte[] stored = datastore.read("a/b.gz");
        assertEquals((byte) 0x1f, stored[0]);
        assertEquals((byte) 0x8b, stored[1]);
        assertEquals("compress me", new String(GzipUtils.decompress(stored), StandardCharsets.UTF_8));
    }

    @Test
    void readGzipRejectsAPlainWrite() {
        datastore.writeString("a/b.gz", "never compressed");

        assertThrows(RuntimeException.class, () -> datastore.readGzipString("a/b.gz"));
    }

    @Test
    void roundTripsAnIngestionManifestAtItsConventionalLocation() {
        IngestionManifest ingestionManifest = manifest("run-1");
        datastore.writeIngestionManifest(ingestionManifest);

        assertTrue(datastore.exists("run-1/ingestionManifest.json"),
                "ingestion manifests are addressed by <id>/ingestionManifest.json");
        assertEquals(ingestionManifest, datastore.readIngestionManifest("run-1"));
    }

    @Test
    void ingestionManifestsForDifferentRunsDoNotCollide() {
        datastore.writeIngestionManifest(manifest("run-1"));
        datastore.writeIngestionManifest(manifest("run-2"));

        assertEquals("run-1", datastore.readIngestionManifest("run-1").id());
        assertEquals("run-2", datastore.readIngestionManifest("run-2").id());
    }

    @Test
    void writtenManifestIsReadableAsRawJson() {
        datastore.writeIngestionManifest(manifest("run-1"));

        String json = datastore.readString("run-1/ingestionManifest.json");
        assertNotNull(json);
        assertTrue(json.contains("\"id\" : \"run-1\""), "expected pretty-printed manifest json, got: " + json);
    }

}
