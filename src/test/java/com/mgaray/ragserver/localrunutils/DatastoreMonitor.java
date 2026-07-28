package com.mgaray.ragserver.localrunutils;

import com.mgaray.ragserver.storage.data.IDatastore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DatastoreMonitor {

    private record Counters(AtomicInteger read, AtomicInteger write, AtomicInteger exists) {
        Counters() {this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger());}
    }

    private final String title;
    private Map<String, Counters> modeToCounters;

    public DatastoreMonitor(String title, long periodMs) {
        this.title = title;
        this.modeToCounters = new ConcurrentHashMap<>();
        start(periodMs);
    }

    public IDatastore add(IDatastore iDatastore) {
        modeToCounters.putIfAbsent(iDatastore.getClass().getSimpleName(), new Counters());
        return new MonitoredDatastore(iDatastore, iDatastore.getClass().getSimpleName());
    }

    private void start(long periodMs) {
        Thread thread = new Thread(() -> {
            while(true) {
                System.out.println(getCounterSummary(null));
                try {
                    Thread.sleep(periodMs);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true); //thread should not keep jvm alive
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(getCounterSummary("final"))));
        thread.start();
    }

    public String getCounterSummary(String qualifier) {
        StringBuilder stringBuilder = new StringBuilder();
        if (qualifier == null) {
            stringBuilder.append("----- " + title + " -----\n");
        } else {
            stringBuilder.append("----- " + title + " ----- (" + qualifier + ") \n");
        }
        for (Map.Entry<String, Counters> entry : modeToCounters.entrySet()) {
            String mode = entry.getKey();
            Counters counters = entry.getValue();
            stringBuilder.append(mode + ": " + counters.read() + "r, " + counters.write() + "w, " + counters.exists() + "e\n");
        }
        return stringBuilder.toString();
    }

    private class MonitoredDatastore implements IDatastore {
        private final IDatastore delegate;
        private final String mode;

        public MonitoredDatastore(IDatastore delegate,String mode) {
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

}
