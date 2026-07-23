package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.common.Models.VectorRecord;

import java.util.List;

public interface IVectorStore<T>  {

    void add(float[] vector, T t);

    List<VectorRecord<T>> get(float[] searchVector, int topK);


}
