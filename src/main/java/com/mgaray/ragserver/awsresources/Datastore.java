package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.S3Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Datastore implements IDatastore {

    private record Counters(AtomicInteger read, AtomicInteger write, AtomicInteger exists) {
        Counters() {this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger());}
    }
    private static Map<Mode, Counters> modeToCounters = new HashMap<>();
    private static boolean completeFlag = false;
    static {
        modeToCounters.put(Mode.IN_MEMORY, new Counters());
        modeToCounters.put(Mode.LOCAL_DISK, new Counters());
        modeToCounters.put(Mode.S3, new Counters());
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(!completeFlag) {
                    System.out.println(getCounters());
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }
    public static String getCounters() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Mode, Counters> entry : modeToCounters.entrySet()) {
            Mode mode = entry.getKey();
            Counters counters = entry.getValue();
            stringBuilder.append(mode + ": " + counters.read() + "r, " + counters.write() + "w, " + counters.exists() + "e\n");
        }
        return stringBuilder.toString();
    }
    public static void complete() {
        completeFlag = true;
    }


    public enum Mode {IN_MEMORY, LOCAL_DISK, S3}

    private final Mode mode;
    private final String bucket;
    private final Map<String, byte[]> inMemoryDatastore;

    public Datastore(Mode mode, String bucket) {
        this.mode = mode;
        this.bucket = bucket;
        this.inMemoryDatastore = Mode.IN_MEMORY.equals(mode) ? new ConcurrentHashMap<>() : null;
    }

    @Override
    public boolean exists(String storageLocation) {
        modeToCounters.get(mode).exists().incrementAndGet();
        return switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.containsKey(storageLocation);
            case LOCAL_DISK -> FileUtils.exists(bucket + "/" + storageLocation);
            case S3 -> S3Utils.exists(bucket, storageLocation);
        };
    }

    @Override
    public void write(String storageLocation, byte[] bytes)  {
        modeToCounters.get(mode).write().incrementAndGet();
        switch(mode) {
            case IN_MEMORY -> inMemoryDatastore.put(storageLocation, bytes);
            case LOCAL_DISK -> FileUtils.writeBytes(bucket + "/" + storageLocation, bytes);
            case S3 -> S3Utils.writeBytes(bucket, storageLocation, bytes);
        }
    }

    @Override
    public byte[] read(String storageLocation) {
        modeToCounters.get(mode).read().incrementAndGet();
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
