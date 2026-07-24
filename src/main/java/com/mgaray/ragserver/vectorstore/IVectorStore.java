package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.common.Models.IVectorRecord;
import com.mgaray.ragserver.common.Models.VectorStoreSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;

import java.util.List;

public interface IVectorStore<T extends IVectorRecord >  {

    void add(float[] vector, T t);

    List<VectorMatch<T>> get(float[] searchVector, int topK);

    void initialize(EmbeddingSpec embeddingSpec);

    void complete(IDatastore datastore, VectorStoreSpec vectorStoreSpec);

}
