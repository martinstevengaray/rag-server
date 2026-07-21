package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class Datastore implements IDatastore {

    public enum Mode {IN_MEMORY, LOCAL_DISK, S3}

    private final Mode mode;
    private final String bucket;
    private final Map<String, byte[]> inMemoryDatastore;

    public Datastore(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
        this.inMemoryDatastore = Mode.IN_MEMORY.equals(mode) ? new HashMap<>() : null;
    }

    @Override
    public boolean exists(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.containsKey(storageLocation);
            case LOCAL_DISK -> FileUtils.exists(bucket + "/" + storageLocation);
            case S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
        };
    }

    @Override
    public byte[] fetchBytes(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.get(storageLocation);
            case LOCAL_DISK -> FileUtils.readBytes(bucket + "/" + storageLocation);
            case S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
        };
    }

    @Override
    public void saveBytes(String storageLocation, byte[] bytes)  {
        switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.put(storageLocation, bytes);
            case LOCAL_DISK -> FileUtils.writeBytes(bucket + "/" + storageLocation, bytes);
            case S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        }
    }

    @Override
    public String fetch(String storageLocation) {
        byte[] bytes = fetchBytes(storageLocation);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, Object> fetchJson(String storageLocation) {
        byte[] bytes = fetchBytes(storageLocation);
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JsonUtils.parse(json);
    }

    @Override
    public List<Map<String, Object>> fetchJsonl(String storageLocation) {
        byte[] bytes = fetchBytes(storageLocation);
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JsonUtils.parseJsonl(json);
    }

    @Override
    public <T> T fetch(String storageLocation, Class<T> clazz) {
        byte[] bytes = fetchBytes(storageLocation);
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JsonUtils.toObject(json, clazz);
    }

    @Override
    public float[] fetchEmbedding(String storageLocation) {
        byte[] bytes = fetchBytes(storageLocation);
        return FileUtils.toFloatArray(bytes);
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
                for (String key : inMemoryDatastore.keySet()) {
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
            case LOCAL_DISK -> FileUtils.listFolder(bucket + "/" + keyPrefix);
            case S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
        };
    }

}
