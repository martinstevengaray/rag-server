package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

public class BucketDelegate {

    public String fetch(Models.StorageLocation storageLocation) {
        if (storageLocation.bucket() == null) {
            return FileUtils.readFile(storageLocation.key());
        }
        return null;
    }

}
