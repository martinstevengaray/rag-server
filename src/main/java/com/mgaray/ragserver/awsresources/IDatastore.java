package com.mgaray.ragserver.awsresources;

import java.util.List;
import java.util.Map;

public interface IDatastore {

    String fetch(String storageLocation);

    Map<String, Object> fetchJson(String storageLocation);

    List<Map<String, Object>> fetchJsonl(String storageLocation);

    <T> T fetch(String storageLocation, Class<T> clazz);

    boolean exists(String storageLocation);

    void save(String storageLocation, Object object);

    void save(String storageLocation, String content);

    float[] fetchEmbedding(String storageLocation);

    void saveEmbedding(String storageLocation, float[] embedding);

    byte[] fetchBytes(String storageLocation);

    void saveBytes(String storageLocation, byte[] bytes);

}
