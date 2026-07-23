package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.common.JsonUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.ArrayList;
import java.util.List;

public class InMemoryVectorStore<T> implements IVectorStore<T> {

    private final InMemoryEmbeddingStore<TextSegment> store;

    public InMemoryVectorStore() {
        this.store = new InMemoryEmbeddingStore<>();
    }

    @Override
    public void add(float[] vector, T t) {
        store.add(new Embedding(vector), TextSegment.from(JsonUtils.toJson(t)));
    }

    @Override
    public List<VectorRecord<T>> get(float[] searchVector, int topK, Class<T> clazz) {
        List<VectorRecord<T>> vectorRecords = new ArrayList<>();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(searchVector))
                .maxResults(topK)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        for (EmbeddingMatch<TextSegment> match : matches) {
            T chunk = JsonUtils.toObject(match.embedded().text(), clazz);
            double matchScore = match.score();
            vectorRecords.add(new VectorRecord<T>(chunk, matchScore));
        }
        return vectorRecords;
    }

}
