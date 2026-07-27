package com.mgaray.ragserver.awsresources;

public class DatastoreCache implements IDatastore {

    private IDatastore[] datastores; //ordered from most volatile to the least volatile

    //instantiate with datastore sequence from most volatile to the least volatile
    //for example: new DatastoreCache(inMemoryDatastore, localDiskDatastore, s3Datastore);
    public DatastoreCache(IDatastore... datastores) {
        this.datastores = datastores;
    }

    @Override
    public boolean exists(String storageLocation) {
        for (IDatastore datastore : datastores) {
           if (datastore.exists(storageLocation)) {
               return true;
           }
        }
        return false;
    }

    @Override
    public void write(String storageLocation, byte[] bytes) {
        for (IDatastore datastore : datastores) {
            datastore.write(storageLocation, bytes);
        }
    }

    @Override
    public byte[] read(String storageLocation) {
        for (int datastoreIndex = 0; datastoreIndex < datastores.length; datastoreIndex++) {
            byte[] bytes = datastores[datastoreIndex].read(storageLocation);
            if (bytes != null) {
                for (int missedIndex = 0; missedIndex < datastoreIndex; missedIndex++) {
                    datastores[missedIndex].write(storageLocation, bytes);
                }
                return bytes;
            }
        }
        return null;
    }

}
