package com.mgaray.ragserver.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipUtils {

    public static byte[] compress(String value) {
        try {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
                gzip.finish();
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decompress(byte[] compressed) {
        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
