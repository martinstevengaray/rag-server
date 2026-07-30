package com.mgaray.ragserver.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GzipUtilsTest {

    @Test
    void roundTripsText() {
        byte[] original = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(original, GzipUtils.decompress(GzipUtils.compress(original)));
    }

    @Test
    void roundTripsEmptyInput() {
        byte[] original = new byte[0];
        assertArrayEquals(original, GzipUtils.decompress(GzipUtils.compress(original)));
    }

    @Test
    void roundTripsBinaryContainingEveryByteValue() {
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) i;
        }
        assertArrayEquals(original, GzipUtils.decompress(GzipUtils.compress(original)));
    }

    @Test
    void roundTripsMultiByteUtf8() {
        String original = "Portland ordinance § 33.110 — café 🌲";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        assertEquals(original, new String(GzipUtils.decompress(GzipUtils.compress(bytes)), StandardCharsets.UTF_8));
    }

    @Test
    void compressesRepetitiveContent() {
        byte[] original = "abcabcabc".repeat(500).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = GzipUtils.compress(original);
        assertTrue(compressed.length < original.length,
                "expected repetitive input to shrink, was " + compressed.length + " vs " + original.length);
        assertArrayEquals(original, GzipUtils.decompress(compressed));
    }

    @Test
    void emitsGzipMagicHeader() {
        byte[] compressed = GzipUtils.compress("payload".getBytes(StandardCharsets.UTF_8));
        assertEquals((byte) 0x1f, compressed[0]);
        assertEquals((byte) 0x8b, compressed[1]);
    }

    @Test
    void decompressRejectsDataThatIsNotGzip() {
        byte[] notGzip = "plain text, never compressed".getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> GzipUtils.decompress(notGzip));
    }

    @Test
    void decompressRejectsTruncatedStream() {
        byte[] compressed = GzipUtils.compress("the quick brown fox".getBytes(StandardCharsets.UTF_8));
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);
        assertThrows(RuntimeException.class, () -> GzipUtils.decompress(truncated));
    }

}
