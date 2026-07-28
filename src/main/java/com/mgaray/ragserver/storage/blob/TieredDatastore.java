package com.mgaray.ragserver.storage.blob;

public class TieredDatastore implements IDatastore {

    private IDatastore[] datastores; //ordered from most volatile to the least volatile

    //instantiate with datastore sequence from most volatile to the least volatile
    //for example: new TieredDatastore(inMemoryDatastore, localDiskDatastore, s3Datastore);
    public TieredDatastore(IDatastore... datastores) {
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
        for (int i=datastores.length-1; i>=0; i--) { //fill least-volatile first
            datastores[i].write(storageLocation, bytes);
        }
    }

    @Override
    public byte[] read(String storageLocation) {
        for (int datastoreIndex = 0; datastoreIndex < datastores.length; datastoreIndex++) {
            byte[] bytes = datastores[datastoreIndex].read(storageLocation);
            if (bytes != null) {
                for (int missedIndex = datastoreIndex - 1; missedIndex >= 0; missedIndex--) {
                    datastores[missedIndex].write(storageLocation, bytes); //backfill least-volatile first
                }
                return bytes;
            }
        }
        return null;
    }

}
