package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

public class DataFetcher {

    public String fetch(Models.StorageLocation storageLocation) {
        if (storageLocation.bucket() == null) {
            return FileUtils.readFile(storageLocation.key());
        }
        return null;
    }

    public String fetchSourceRecordText(Models.SourceRecord sourceRecord) {
        if (sourceRecord.lazyText() != null) {
            return sourceRecord.lazyText();
        }
        return fetch(sourceRecord.textLocation());
    }

}
