package com.mgaray.ragserver.awsresources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDatastore implements IDatastore {

    private final Map<String, byte[]> inMemoryDatastore = new ConcurrentHashMap<>();

    public InMemoryDatastore() {}

    @Override
    public byte[] read(String storageLocation) {
        return inMemoryDatastore.get(storageLocation);
    }

    @Override
    public void write(String storageLocation, byte[] bytes)  {
        inMemoryDatastore.put(storageLocation, bytes);
    }

    @Override
    public boolean exists(String storageLocation) {
        return inMemoryDatastore.containsKey(storageLocation);
    }

}
