package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DataFetcher {

    public enum Mode {IN_MEMORY, ON_DISK, ON_S3}


    private Map<String, Object> inMemoryDataStore = new HashMap<>();

    private final Mode mode;
    private final String bucket;

    public DataFetcher(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
    }

    public String fetch(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> (String) inMemoryDataStore.get(storageLocation);
            case ON_DISK -> FileUtils.readFile(bucket + "/" + storageLocation);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
    }

    public void save(String storageLocation, String content) {
        switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.put(storageLocation, content);
            case ON_DISK -> FileUtils.writeFile(bucket + "/" + storageLocation, content);
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

    public void save(String storageLocation, float[] embedding) {
        switch(mode) {
            case IN_MEMORY -> inMemoryDataStore.put(storageLocation, embedding);
            case ON_DISK -> FileUtils.writeFile(bucket + "/" + storageLocation, Arrays.toString(embedding)); //todo bin format
            case ON_S3 -> throw new UnsupportedOperationException("Unsupported mode: " + mode);
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

//    public String fetchSourceRecordText(Models.SourceRecord sourceRecord) {
//        if (sourceRecord.lazyText() != null) {
//            return sourceRecord.lazyText();
//        }
//        return fetch(sourceRecord.textLocation());
//    }

}
