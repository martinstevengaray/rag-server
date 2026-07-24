package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.common.Models.IVectorRecord;
import com.mgaray.ragserver.common.Models.IngestionManifest;

import java.util.List;

public interface IVectorStore<T extends IVectorRecord >  {

    void add(float[] vector, T t);

    List<VectorMatch<T>> get(float[] searchVector, int topK);

    void complete(IDatastore datastore, Models.VectorStoreSpec vectorStoreSpec);

}
