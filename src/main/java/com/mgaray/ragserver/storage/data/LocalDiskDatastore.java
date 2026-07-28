package com.mgaray.ragserver.storage.data;

import java.nio.file.Files;
import java.nio.file.Path;

public class LocalDiskDatastore implements IDatastore {

    private final String bucket;

    public LocalDiskDatastore(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public byte[] read(String storageLocation) {
        try {
            Path path = Path.of(bucket + "/" + storageLocation);
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
            //throw new RuntimeException(e);
        }
    }

    @Override
    public void write(String storageLocation, byte[] bytes)  {
        try {
            Path path = Path.of(bucket + "/" + storageLocation);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(String storageLocation) {
        Path path = Path.of(bucket + "/" + storageLocation);
        return  Files.exists(path);
    }

}
