package com.mgaray.ragserver.chunker;

public class LocationConventions {

    public static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }
}
