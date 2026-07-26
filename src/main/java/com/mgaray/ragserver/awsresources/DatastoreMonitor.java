package com.mgaray.ragserver.awsresources;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DatastoreMonitor implements IDatastore {

    private record Counters(AtomicInteger read, AtomicInteger write, AtomicInteger exists) {
        Counters() {this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger());}
    }
    private static Map<Datastore.Mode, Counters> modeToCounters = new HashMap<>();
    private static boolean completeFlag = false;
    static {
        modeToCounters.put(Datastore.Mode.IN_MEMORY, new Counters());
        modeToCounters.put(Datastore.Mode.LOCAL_DISK, new Counters());
        modeToCounters.put(Datastore.Mode.S3, new Counters());
        new Thread(() -> {
            while(!completeFlag) {
                System.out.println(getCounters());
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
    private static String getCounters() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Datastore.Mode, Counters> entry : modeToCounters.entrySet()) {
            Datastore.Mode mode = entry.getKey();
            Counters counters = entry.getValue();
            stringBuilder.append(mode + ": " + counters.read() + "r, " + counters.write() + "w, " + counters.exists() + "e\n");
        }
        return stringBuilder.toString();
    }
    public static void stop() {
        completeFlag = true;
    }

    private final IDatastore delegate;
    private final Datastore.Mode mode;
    public DatastoreMonitor(IDatastore delegate, Datastore.Mode mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override
    public boolean exists(String storageLocation) {
        modeToCounters.get(mode).exists().incrementAndGet();
        return delegate.exists(storageLocation);
    }

    @Override
    public void write(String storageLocation, byte[] bytes) {
        modeToCounters.get(mode).write().incrementAndGet();
        delegate.write(storageLocation, bytes);
    }

    @Override
    public byte[] read(String storageLocation) {
        modeToCounters.get(mode).read().incrementAndGet();
        return delegate.read(storageLocation);
    }
}
