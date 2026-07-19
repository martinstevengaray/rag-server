package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.JsonUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.ArrayList;
import java.util.List;

public class VectorStore {

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final DataFetcher dataFetcher;

    public VectorStore(DataFetcher dataFetcher) {
        this.store = new InMemoryEmbeddingStore<>();
        this.dataFetcher = dataFetcher;
    }

//    public VectorStore(DataFetcher dataFetcher, String sourceManifestId) { //, String modelName) {
//        this.dataFetcher = dataFetcher;
//        String location = Models.vectorStore(sourceManifestId, modelName);
//        this.store = InMemoryEmbeddingStore.fromJson(dataFetcher.fetch(location));
//    }

//    public void load(Models.SourceManifest sourceManifest) {
//        manifest.
//
//        //todo
//    }

    public void add(float[] vector, Models.Chunk chunk) {
        store.add(new Embedding(vector), TextSegment.from(JsonUtils.toJson(chunk)));
    }

    public List<Models.ChunkMatch> get(float[] searchVector, int count) {
        List<Models.ChunkMatch> chunkMatches = new ArrayList<>();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(searchVector))
                .maxResults(count)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        for (EmbeddingMatch<TextSegment> match : matches) {
            Models.Chunk chunk = JsonUtils.toObject(match.embedded().text(), Models.Chunk.class);
            double matchScore = match.score();
            chunkMatches.add(new Models.ChunkMatch(chunk, matchScore));
        }
        return chunkMatches;
    }

    public void save(String sourceManifestId, String modelName) {
        String location = Models.vectorStore(sourceManifestId, modelName);
        String storeJson = store.serializeToJson();
        dataFetcher.save(location, storeJson);
    }

}
