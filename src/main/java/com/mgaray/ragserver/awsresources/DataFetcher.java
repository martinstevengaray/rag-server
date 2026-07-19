package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.JsonUtils;

import java.util.*;

public class DataFetcher {

    public enum Mode {IN_MEMORY, ON_DISK, ON_S3}


    private Map<String, Object> inMemoryDataStore = new HashMap<>();

    private final Mode mode;
    private final String bucket;

    public DataFetcher(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
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

    public String fetch(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> (String) inMemoryDataStore.get(storageLocation);
            case ON_DISK -> FileUtils.readFile(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
    }

    public Map<String, Object> fetchJson(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> JsonUtils.parse((String)inMemoryDataStore.get(storageLocation));
            case ON_DISK -> FileUtils.readJsonFile(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
    }

    public <T> T fetch(String storageLocation, Class<T> clazz) {
        return switch(mode) {
            case IN_MEMORY -> JsonUtils.toObject((String) inMemoryDataStore.get(storageLocation), clazz);
            case ON_DISK -> JsonUtils.toObject(FileUtils.readFile(bucket + "/" + storageLocation), clazz);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        };
    }

    public boolean exists(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.containsKey(storageLocation);
            case ON_DISK -> FileUtils.exists(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        };
    }

    public void save(String storageLocation, Object object) {
        save(storageLocation, JsonUtils.toJsonPretty(object));
    }

    public void save(String storageLocation, String content) {
        switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.put(storageLocation, content);
            case ON_DISK -> FileUtils.writeFile(bucket + "/" + storageLocation, content);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        }
    }

    public void save(String storageLocation, float[] embedding) {
        switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.put(storageLocation, embedding);
            case ON_DISK -> FileUtils.writeFile(bucket + "/" + storageLocation, Arrays.toString(embedding)); //todo bin format
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unexpected mode: " + mode);
        }
    }

}
