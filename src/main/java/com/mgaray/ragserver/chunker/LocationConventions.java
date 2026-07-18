package com.mgaray.ragserver.chunker;

public class LocationConventions {

    public static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }

    public static String chunkManifestLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

    public static String chunkTextLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunks/" + chunkId + ".txt";
    }

    public static String embeddingLocation(String sourceManifestId, String sourceRecordId, String modelName, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/embeddings/" + modelName + "-" + chunkId + ".bin";
    }

    public static String sourceManifestLocation(String sourceManifestId) {
        return "/" + sourceManifestId + "/sourceManifest.json";
    }

}
