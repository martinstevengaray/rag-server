package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.ByteUtils;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;

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

}
