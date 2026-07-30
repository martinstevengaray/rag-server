package com.mgaray.ragserver.storage.data;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TieredDatastoreTest {

    /**
     * An in-memory datastore that records the reads and writes it is asked to perform, both per
     * tier and onto a log shared across tiers so the order operations fan out can be asserted.
     */
    private static class RecordingDatastore extends InMemoryDatastore {
        private final String name;
        private final List<String> tierLog;
        private final List<String> reads = new ArrayList<>();
        private final List<String> writes = new ArrayList<>();

        RecordingDatastore(String name, List<String> tierLog) {
            this.name = name;
            this.tierLog = tierLog;
        }

        @Override
        public byte[] read(String storageLocation) {
            reads.add(storageLocation);
            tierLog.add("read " + name);
            return super.read(storageLocation);
        }

        @Override
        public void write(String storageLocation, byte[] bytes) {
            writes.add(storageLocation);
            tierLog.add("write " + name);
            super.write(storageLocation, bytes);
        }
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private final List<String> tierLog = new ArrayList<>();
    private final RecordingDatastore volatileTier = new RecordingDatastore("memory", tierLog);
    private final RecordingDatastore middleTier = new RecordingDatastore("disk", tierLog);
    private final RecordingDatastore durableTier = new RecordingDatastore("s3", tierLog);
    private final TieredDatastore tiered = new TieredDatastore(volatileTier, middleTier, durableTier);

    @Test
    void writeFansOutToEveryTier() {
        tiered.write("a/b.txt", bytes("content"));

        assertArrayEquals(bytes("content"), volatileTier.read("a/b.txt"));
        assertArrayEquals(bytes("content"), middleTier.read("a/b.txt"));
        assertArrayEquals(bytes("content"), durableTier.read("a/b.txt"));
    }

    @Test
    void writeFillsTheLeastVolatileTierFirst() {
        tiered.write("a/b.txt", bytes("content"));

        // ordering matters: a crash mid-write must never leave a cache hit that the durable tier lacks
        assertEquals(List.of("write s3", "write disk", "write memory"), tierLog);
    }

    @Test
    void readBackfillFillsTheLeastVolatileTierFirst() {
        durableTier.write("a/b.txt", bytes("content"));
        tierLog.clear();

        tiered.read("a/b.txt");

        // the two misses, then the backfill working back up towards the most volatile tier
        assertEquals(List.of("read memory", "read disk", "read s3", "write disk", "write memory"), tierLog);
    }

    @Test
    void readServesFromTheMostVolatileTierWithoutTouchingTheOthers() {
        tiered.write("a/b.txt", bytes("content"));
        middleTier.reads.clear();
        durableTier.reads.clear();

        assertArrayEquals(bytes("content"), tiered.read("a/b.txt"));

        assertTrue(middleTier.reads.isEmpty(), "a cache hit should not reach the middle tier");
        assertTrue(durableTier.reads.isEmpty(), "a cache hit should not reach the durable tier");
    }

    @Test
    void readBackfillsTheMoreVolatileTiersOnAMiss() {
        durableTier.write("a/b.txt", bytes("content"));
        durableTier.writes.clear();

        assertArrayEquals(bytes("content"), tiered.read("a/b.txt"));

        assertArrayEquals(bytes("content"), volatileTier.read("a/b.txt"));
        assertArrayEquals(bytes("content"), middleTier.read("a/b.txt"));
        assertEquals(List.of("a/b.txt"), middleTier.writes);
        assertEquals(List.of("a/b.txt"), volatileTier.writes);
        assertTrue(durableTier.writes.isEmpty(), "the tier that served the read should not be rewritten");
    }

    @Test
    void readBackfillsOnlyTiersAboveTheHit() {
        middleTier.write("a/b.txt", bytes("content"));
        durableTier.writes.clear();

        assertArrayEquals(bytes("content"), tiered.read("a/b.txt"));

        assertArrayEquals(bytes("content"), volatileTier.read("a/b.txt"));
        assertTrue(durableTier.writes.isEmpty(), "tiers below the hit are not populated by a read");
        assertNull(durableTier.read("a/b.txt"));
    }

    @Test
    void readReturnsNullWhenNoTierHasTheLocation() {
        assertNull(tiered.read("nothing/here"));

        assertEquals(List.of("nothing/here"), volatileTier.reads);
        assertEquals(List.of("nothing/here"), middleTier.reads);
        assertEquals(List.of("nothing/here"), durableTier.reads);
    }

    @Test
    void existsIsTrueWhenAnyTierHasTheLocation() {
        durableTier.write("a/b.txt", bytes("content"));

        assertTrue(tiered.exists("a/b.txt"));
    }

    @Test
    void existsIsFalseWhenNoTierHasTheLocation() {
        assertFalse(tiered.exists("nothing/here"));
    }

    @Test
    void existsShortCircuitsOnTheFirstHit() {
        volatileTier.write("a/b.txt", bytes("content"));

        assertTrue(tiered.exists("a/b.txt"));
        assertNull(durableTier.read("a/b.txt"));
    }

    @Test
    void aSingleTierBehavesLikeThatTier() {
        TieredDatastore single = new TieredDatastore(volatileTier);

        single.write("a/b.txt", bytes("content"));

        assertArrayEquals(bytes("content"), single.read("a/b.txt"));
        assertTrue(single.exists("a/b.txt"));
        assertNull(single.read("nothing/here"));
    }

    @Test
    void convenienceMethodsWorkThroughTheTiers() {
        tiered.writeGzipString("a/b.gz", "compress me");

        assertEquals("compress me", tiered.readGzipString("a/b.gz"));
        assertEquals("compress me", durableTier.readGzipString("a/b.gz"));
    }

}
