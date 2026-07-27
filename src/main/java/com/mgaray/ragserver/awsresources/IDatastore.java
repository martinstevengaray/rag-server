package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.ByteUtils;
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
        return ByteUtils.toFloatArray(read(storageLocation));
    }

    //convenience write methods
    default void writeObject(String storageLocation, Object object) {
        write(storageLocation, JsonUtils.toJsonPretty(object).getBytes(StandardCharsets.UTF_8));
    }
    default void writeString(String storageLocation, String content) {
        write(storageLocation, content.getBytes(StandardCharsets.UTF_8));
    }
    default void writeEmbedding(String storageLocation, float[] embedding) {
        write(storageLocation, ByteUtils.toBytes(embedding));
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

}
