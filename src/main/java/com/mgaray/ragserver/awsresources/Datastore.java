package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.S3Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Datastore implements IDatastore {

    public enum Mode {IN_MEMORY, LOCAL_DISK, S3}

    private final Mode mode;
    private final String bucket;
    private final Map<String, byte[]> inMemoryDatastore;

    public Datastore(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
        this.inMemoryDatastore = Mode.IN_MEMORY.equals(mode) ? new ConcurrentHashMap<>() : null;
    }

    public IDatastore monitor() {
        return new DatastoreMonitor(this, mode);
    }

    @Override
    public boolean exists(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.containsKey(storageLocation);
            case LOCAL_DISK -> FileUtils.exists(bucket + "/" + storageLocation);
            case S3 -> S3Utils.exists(bucket, storageLocation);
        };
    }

    @Override
    public void write(String storageLocation, byte[] bytes)  {
        switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.put(storageLocation, bytes);
            case LOCAL_DISK -> FileUtils.writeBytes(bucket + "/" + storageLocation, bytes);
            case S3 -> S3Utils.writeBytes(bucket, storageLocation, bytes);
        }
    }

    @Override
    public byte[] read(String storageLocation) {
        return switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.get(storageLocation);
            case LOCAL_DISK -> FileUtils.readBytes(bucket + "/" + storageLocation);
            case S3 -> S3Utils.readBytes(bucket, storageLocation);
        };
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
            case S3 -> S3Utils.list(bucket, keyPrefix);
        };
    }

}
