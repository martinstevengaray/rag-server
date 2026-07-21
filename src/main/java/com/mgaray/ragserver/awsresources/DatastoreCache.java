package com.mgaray.ragserver.awsresources;

import java.util.ArrayList;
import java.util.List;

public class DatastoreCache implements IDatastore {

    private IDatastore primary;
    private IDatastore[] secondaries;

    public DatastoreCache(IDatastore primary, IDatastore... secondaries) { //secondaries in order of search
        this.primary = primary;
        this.secondaries = secondaries;
    }

    @Override
    public boolean exists(String storageLocation) {
        for (IDatastore secondary : secondaries) {
           if (secondary.exists(storageLocation)) {
               return true;
           }
        }
        return primary.exists(storageLocation);
    }

    @Override
    public void write(String storageLocation, byte[] bytes) {
        primary.write(storageLocation, bytes);
        for (IDatastore secondary : secondaries) {
            secondary.write(storageLocation, bytes);
        }
    }

    @Override
    public byte[] read(String storageLocation) {
        List<IDatastore> missedReads = new ArrayList<>();
        for (IDatastore secondary : secondaries) {
            byte[] bytes = secondary.read(storageLocation);
            if (bytes == null) {
                missedReads.add(secondary);
            } else {
                for (IDatastore missed : missedReads) {
                    missed.write(storageLocation, bytes);
                }
                return bytes;
            }
        }
        return null;
    }

}
