package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class Datastore implements IDatastore {

    public enum Mode {IN_MEMORY, ON_DISK, ON_S3}  //{IN_MEMORY, ON_DISK, ON_S3} //IN_MEMORY, FILE_SYSTEM, S3

    private Map<String, Object> inMemoryDataStore = new HashMap<>();

    private final Mode mode;
    private final String bucket;

    public Datastore(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
    }

    @Override
    public boolean exists(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.containsKey(storageLocation);
            case ON_DISK -> FileUtils.exists(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
        };
    }

    private<R> R readResult(String storageLocation, Function<byte[], R> function) {
        byte[] bytes = null;
        switch(mode) {
            case IN_MEMORY -> { return (R)inMemoryDataStore.get(storageLocation); }
            case ON_DISK -> bytes = FileUtils.readBytes(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
        }
        return function.apply(bytes);
    }

    @Override
    public void saveBytes(String storageLocation, byte[] bytes)  {
        switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.put(storageLocation, bytes);
            case ON_DISK -> FileUtils.writeBytes(bucket + "/" + storageLocation, bytes);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        }
    }

    @Override
    public String fetch(String storageLocation) {
        return readResult(storageLocation, (byte[] bytes) -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public Map<String, Object> fetchJson(String storageLocation) {
        return readResult(storageLocation, (byte[] bytes) -> {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return JsonUtils.parse(json);
        });
    }

    @Override
    public List<Map<String, Object>> fetchJsonl(String storageLocation) {
        return readResult(storageLocation, (byte[] bytes) -> {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return JsonUtils.parseJsonl(json);
        });
    }

    @Override
    public <T> T fetch(String storageLocation, Class<T> clazz) {
        return readResult(storageLocation, (byte[] bytes) -> {
            String json = new String(bytes, StandardCharsets.UTF_8);
            return JsonUtils.toObject(json, clazz);
        });
    }

    @Override
    public float[] fetchEmbedding(String storageLocation) {
        return readResult(storageLocation, FileUtils::toFloatArray);
    }

    public byte[] fetchBytes(String storageLocation) {
        return readResult(storageLocation, (byte[] bytes) -> bytes);
    }

    @Override
    public void save(String storageLocation, Object object) {
        saveBytes(storageLocation, JsonUtils.toJsonPretty(object).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void save(String storageLocation, String content) {
        saveBytes(storageLocation, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void saveEmbedding(String storageLocation, float[] embedding) {
        saveBytes(storageLocation, FileUtils.toBytes(embedding));
    }

    public List<String> list(String keyPrefix) {
        return switch(mode) {
            case IN_MEMORY -> {
                List<String> matchingKeys = new ArrayList<>();
                for (String key : inMemoryDataStore.keySet()) {
                    if (key.startsWith(keyPrefix)) {
                        String postfix = key.substring(keyPrefix.length());
                        if (postfix.contains("/")) {
                            postfix = postfix.split("/")[0];
                        }
                        matchingKeys.add(postfix);
                    }
                }
                yield matchingKeys;
            }
            case ON_DISK -> FileUtils.listFolder(bucket + "/" + keyPrefix);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
    }

}
