package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.GzipUtils;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.VectorStoreSpec;
import com.mgaray.ragserver.common.Models.VectorMatch;
import com.mgaray.ragserver.common.Models.IVectorRecord;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVectorStore<T extends IVectorRecord> implements IVectorStore<T> {

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final Class<T> clazz;
    private final Map<String, T> idToT;

    public InMemoryVectorStore(Class<T> clazz) {
        this(new InMemoryEmbeddingStore<>(), clazz);
    }

    private InMemoryVectorStore(InMemoryEmbeddingStore<TextSegment> store, Class<T> clazz) {
        this.store = store;
        this.clazz = clazz;
        this.idToT = new ConcurrentHashMap<>();
    }

    @Override
    public void add(float[] vector, T t) {
        store.remove(t.id());
        store.add(t.id(), new Embedding(vector), TextSegment.from(JsonUtils.toJson(t)));
        idToT.put(t.id(), t);
    }

    @Override
    public List<VectorMatch<T>> get(float[] searchVector, int topK) {
        List<VectorMatch<T>> vectorMatches = new ArrayList<>();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(searchVector))
                .maxResults(topK)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        for (EmbeddingMatch<TextSegment> match : matches) {
            T chunk = JsonUtils.toObject(match.embedded().text(), clazz);
            double matchScore = match.score();
            vectorMatches.add(new VectorMatch<T>(chunk, matchScore));
        }
        return vectorMatches;
    }

    public T get(String id) {
        return idToT.get(id);
    }

    @Override
    public void initialize(EmbeddingSpec embeddingSpec) {
        //mothing to do
    }

    @Override
    public boolean resultsExist(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
        String inMemoryVectorStoreExportLocation = vectorStoreSpec.inMemoryVectorStoreExportLocation();
        return datastore.exists(inMemoryVectorStoreExportLocation);
    }

    @Override
    public void writeResults(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
        String inMemoryVectorStoreExportLocation = vectorStoreSpec.inMemoryVectorStoreExportLocation();
        datastore.writeGzipString(inMemoryVectorStoreExportLocation, store.serializeToJson());
    }

    @Override
    public boolean exists(T t) {
        return idToT.containsKey(t.id());
    }

    //-----to load contents from disk (unique to InMemoryVectorStore)---------------------------------------------------

    public static<T extends IVectorRecord> InMemoryVectorStore<T> load(IDatastore datastore,
                                                                       String inMemoryVectorStoreExportLocation,
                                                                       Class<T> clazz) {
        String vectorStoreJson = datastore.readGzipString(inMemoryVectorStoreExportLocation);
        InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromJson(vectorStoreJson);
        InMemoryVectorStore<T> vectorStore = new InMemoryVectorStore<>(store, clazz);
        //loop through contents from vectorStoreJson to load idToT lookup map
        for (Map<String, Object> entry : (List<Map<String, Object>>) JsonUtils.parse(vectorStoreJson).get("entries")) {
            String recordJson = JsonUtils.getNestedField(entry, "embedded", "text");
            T t = JsonUtils.toObject(recordJson, clazz);
            vectorStore.idToT.put(t.id(), t);
        }
        return vectorStore;
    }


}
