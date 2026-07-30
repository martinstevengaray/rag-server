package com.mgaray.ragserver;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.Source;


public class CopySourceCatalogToS3 {

    public static void main(String[] args) {
        String localSourceRoot = "local/sources_new";
        String s3SourceBucket = IngestionMain.s3SourceBucket;
        String sourceCatalogLocation = "portland-city-code/sourceCatalog.json";

        IDatastore localSourceDatastore = new LocalDiskDatastore(localSourceRoot);
        IDatastore s3SourceDatastore = new S3Datastore(s3SourceBucket);

        SourceCatalog sourceCatalog = localSourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);
        s3SourceDatastore.writeObject(sourceCatalogLocation, sourceCatalog);

        for (Source source: sourceCatalog.sources()) {
            String sourceLocation = source.location();
            String contents = localSourceDatastore.readString(sourceLocation);
            s3SourceDatastore.writeString(sourceLocation, contents);
        }

    }

}
