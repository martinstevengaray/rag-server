package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.GzipUtils;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public interface IDatastore {

    //primary operations that need to be overwritten per datastore technology
    byte[] read(String storageLocation);
    void write(String storageLocation, byte[] bytes);
    boolean exists(String storageLocation);

    //convenience read methods
    default <T> T readObject(String storageLocation, Class<T> clazz) {
        String json = new String(read(storageLocation), StandardCharsets.UTF_8);
        return JsonUtils.toObject(json, clazz);
    }
    default String readString(String storageLocation) {
        return new String(read(storageLocation), StandardCharsets.UTF_8);
    }
    default float[] readFloatArray(String storageLocation) {
        return toFloatArray(read(storageLocation));
    }
    default byte[] readGzip(String storageLocation) {
        return GzipUtils.decompress(read(storageLocation));
    }
    default String readGzipString(String storageLocation) {
        return new String(GzipUtils.decompress(read(storageLocation)), StandardCharsets.UTF_8);
    }

    //convenience write methods
    default void writeObject(String storageLocation, Object object) {
        write(storageLocation, JsonUtils.toJsonPretty(object).getBytes(StandardCharsets.UTF_8));
    }
    default void writeString(String storageLocation, String content) {
        write(storageLocation, content.getBytes(StandardCharsets.UTF_8));
    }
    default void writeFloatArray(String storageLocation, float[] embedding) {
        write(storageLocation, toBytes(embedding));
    }
    default void writeGzip(String storageLocation, byte[] bytes) {
        write(storageLocation, GzipUtils.compress(bytes));
    }
    default void writeGzipString(String storageLocation, String content) {
        write(storageLocation, GzipUtils.compress(content.getBytes(StandardCharsets.UTF_8)));
    }

    //convenience methods specific to models
    default IngestionManifest readIngestionManifest(String ingestionManifestId) {
        String ingestionManifestLocation = ingestManifestLocation(ingestionManifestId);
        return readObject(ingestionManifestLocation, IngestionManifest.class);
    }
    default void writeIngestionManifest(IngestionManifest ingestionManifest) {
        String ingestionManifestLocation = ingestManifestLocation(ingestionManifest.id());
        writeObject(ingestionManifestLocation, ingestionManifest);
    }
    private static String ingestManifestLocation(String ingestionManifestId) {
        return ingestionManifestId + "/ingestionManifest.json";
    }

    //float array transformers
    private static float[] toFloatArray(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Byte array length must be a multiple of " + Float.BYTES);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }

    private static byte[] toBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

}
