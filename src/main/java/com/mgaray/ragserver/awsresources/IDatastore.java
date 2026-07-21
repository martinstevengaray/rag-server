package com.mgaray.ragserver.awsresources;

import java.util.List;
import java.util.Map;

public interface IDatastore {

    boolean exists(String storageLocation);
    void write(String storageLocation, byte[] bytes);
    byte[] read(String storageLocation);

    void writeObject(String storageLocation, Object object);
    void writeString(String storageLocation, String content);
    void writeEmbedding(String storageLocation, float[] embedding);

    <T> T readObject(String storageLocation, Class<T> clazz);
    String readString(String storageLocation);
    float[] readEmbedding(String storageLocation);

    Map<String, Object> readJson(String storageLocation);
    List<Map<String, Object>> readJsonl(String storageLocation);

}
