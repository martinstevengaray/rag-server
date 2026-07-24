package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.common.Models.VectorMatch;

import java.util.List;

public interface IVectorStore<T>  {

    void add(float[] vector, T t);

    List<VectorMatch<T>> get(float[] searchVector, int topK);


}
