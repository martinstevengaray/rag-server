package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IVectorRecord;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.VectorStoreSpec;

import java.util.List;

public class MultiVectorStore<T extends IVectorRecord> implements IVectorStore<T> {

    private final IVectorStore<T>[] iVectorStores;

    public MultiVectorStore(IVectorStore<T>... iVectorStore) {
        this.iVectorStores = iVectorStore;
    }

    @Override
    public void add(float[] vector, T t) {
        for (IVectorStore<T> iVectorStore : iVectorStores) {
            iVectorStore.add(vector, t);
        }
    }

    @Override
    public List<VectorMatch<T>> get(float[] searchVector, int topK) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize(EmbeddingSpec embeddingSpec) {
        for (IVectorStore<T> iVectorStore : iVectorStores) {
            iVectorStore.initialize(embeddingSpec);
        }
    }

    @Override
    public void complete(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
        for (IVectorStore<T> iVectorStore : iVectorStores) {
            iVectorStore.complete(datastore, vectorStoreSpec);
        }

    }

    @Override
    public boolean exists(T t) { //if it exists in all the underlying iVectorStores
        for (IVectorStore<T> iVectorStore : iVectorStores) {
            if (!iVectorStore.exists(t)) {
                return false;
            }
        }
        return (iVectorStores.length != 0);
    }
}
