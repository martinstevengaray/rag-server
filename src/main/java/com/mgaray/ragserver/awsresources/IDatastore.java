package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.JsonUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
    default float[] readEmbedding(String storageLocation) {
        return toFloatArray(read(storageLocation));
    }

    //convenience write methods
    default void writeObject(String storageLocation, Object object) {
        write(storageLocation, JsonUtils.toJsonPretty(object).getBytes(StandardCharsets.UTF_8));
    }
    default void writeString(String storageLocation, String content) {
        write(storageLocation, content.getBytes(StandardCharsets.UTF_8));
    }
    default void writeEmbedding(String storageLocation, float[] embedding) {
        write(storageLocation, toBytes(embedding));
    }

    //used upstream only, as convenience methods for transforming arbitrary sources
    default Map<String, Object> readJson(String storageLocation) {
        String json = new String(read(storageLocation), StandardCharsets.UTF_8);
        return JsonUtils.parse(json);
    }
    default List<Map<String, Object>> readJsonl(String storageLocation) {
        String json = new String(read(storageLocation), StandardCharsets.UTF_8);
        return JsonUtils.parseJsonl(json);
    }

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
