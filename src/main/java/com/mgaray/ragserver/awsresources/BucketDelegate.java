package com.mgaray.ragserver.awsresources;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

public class BucketDelegate {

    public String fetch(Models.Resource resource) {
        if (resource.bucket() == null) {
            return FileUtils.readFile(resource.key());
        }
        return null;
    }

}
