package com.mgaray.ragserver.storage.data;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiskDatastoreTest {

    @TempDir
    Path bucket;

    private LocalDiskDatastore datastore() {
        return new LocalDiskDatastore(bucket.toString());
    }

    @Test
    void writeCreatesTheFileUnderTheBucket() {
        datastore().writeString("run-1/sourceRecords/a/sourceRecord.txt", "content");

        assertTrue(Files.exists(bucket.resolve("run-1/sourceRecords/a/sourceRecord.txt")));
    }

    @Test
    void writeCreatesMissingParentDirectories() {
        datastore().write("deeply/nested/path/file.bin", new byte[]{1, 2, 3, 4});

        assertArrayEquals(new byte[]{1, 2, 3, 4}, datastore().read("deeply/nested/path/file.bin"));
    }

    @Test
    void writeAtTheBucketRootNeedsNoParent() {
        datastore().writeString("file.txt", "content");

        assertEquals("content", datastore().readString("file.txt"));
    }

    @Test
    void roundTripsBytes() {
        byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        datastore().write("a/b.txt", content);

        assertArrayEquals(content, datastore().read("a/b.txt"));
    }

    @Test
    void writeOverwritesAnExistingFile() {
        datastore().writeString("a/b.txt", "first");
        datastore().writeString("a/b.txt", "second");

        assertEquals("second", datastore().readString("a/b.txt"));
    }

    @Test
    void readReturnsNullForAMissingFile() {
        assertNull(datastore().read("nothing/here.txt"));
    }

    @Test
    void readReturnsNullForADirectory() {
        datastore().writeString("a/b.txt", "content");

        // a directory is not readable content; TieredDatastore relies on null meaning "miss"
        assertNull(datastore().read("a"));
    }

    @Test
    void existsReflectsWhatIsOnDisk() {
        assertFalse(datastore().exists("a/b.txt"));

        datastore().writeString("a/b.txt", "content");

        assertTrue(datastore().exists("a/b.txt"));
    }

    @Test
    void separateBucketsAreIsolated() {
        LocalDiskDatastore first = new LocalDiskDatastore(bucket.resolve("one").toString());
        LocalDiskDatastore second = new LocalDiskDatastore(bucket.resolve("two").toString());

        first.writeString("a/b.txt", "content");

        assertTrue(first.exists("a/b.txt"));
        assertFalse(second.exists("a/b.txt"));
    }

    @Test
    void survivesReinstantiationOfTheDatastore() {
        datastore().writeGzipString("run-1/vectorStore.json.gz", "persisted");

        assertEquals("persisted", new LocalDiskDatastore(bucket.toString()).readGzipString("run-1/vectorStore.json.gz"));
    }

}
