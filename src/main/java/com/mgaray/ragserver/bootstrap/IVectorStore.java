package com.mgaray.ragserver.bootstrap;

import java.util.List;

public interface IVectorStore<T>  {

    void add(float[] vector, T t);

    List<VectorRecord<T>> get(float[] searchVector, int topK);

    record VectorRecord<T> (T t, double matchScore) {}

}
