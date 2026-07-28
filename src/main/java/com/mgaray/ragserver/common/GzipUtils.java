package com.mgaray.ragserver.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipUtils {

    public static byte[] compress(byte[] value) {
        try {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value);
                gzip.finish();
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decompress(byte[] compressed) {
        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
