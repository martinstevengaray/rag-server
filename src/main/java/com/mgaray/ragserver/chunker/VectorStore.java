package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.ArrayList;
import java.util.List;

public class VectorStore {

    private InMemoryEmbeddingStore<Models.Chunk> store;  //todo change to TextSegment type if we want to rehydrate
    private final DataFetcher dataFetcher;

    public VectorStore(DataFetcher dataFetcher) {
        this.store = new InMemoryEmbeddingStore<>();
        this.dataFetcher = dataFetcher;
    }

    public void add(float[] vector, Models.Chunk chunk) {
        store.add(new Embedding(vector), chunk);
    }

    public List<Models.ChunkMatch> get(float[] searchVector, int count) {
        List<Models.ChunkMatch> chunkMatches = new ArrayList<>();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(searchVector))
                .maxResults(count)
                .build();
        List<EmbeddingMatch<Models.Chunk>> matches = store.search(request).matches();
        for (EmbeddingMatch<Models.Chunk> match : matches) {
            Models.Chunk chunk = match.embedded();
            double matchScore = match.score();
            chunkMatches.add(new Models.ChunkMatch(chunk, matchScore));
        }
        return chunkMatches;
    }

    public void save(String location) {
        String storeJson = store.serializeToJson();
        dataFetcher.save(location, storeJson);
    }

//    public void load(String location) {
//        String storeJson = dataFetcher.fetch(location);
//        store = InMemoryEmbeddingStore.<Models.Chunk>fromJson(storeJson);
//    }

}
