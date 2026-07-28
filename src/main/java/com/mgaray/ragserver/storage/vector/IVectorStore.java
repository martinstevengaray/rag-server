package com.mgaray.ragserver.storage.vector;

import com.mgaray.ragserver.storage.blob.IDatastore;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.IVectorRecord;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.Models.EmbeddingSpec;

import java.util.List;

public interface IVectorStore<T extends IVectorRecord >  {

    void add(float[] vector, T t);

    List<VectorMatch<T>> get(float[] searchVector, int topK);

    T get(String id);

    void initialize(EmbeddingSpec embeddingSpec);

    boolean resultsExist(IDatastore datastore, VectorStoreSpec vectorStoreSpec);

    void writeResults(IDatastore datastore, VectorStoreSpec vectorStoreSpec);

    boolean exists(T t);

}
