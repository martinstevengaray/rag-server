package com.mgaray.ragserver.vectorstore;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.VectorMatch;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.mgaray.ragserver.common.Models.vectorStoreLocation;

public class InMemoryVectorStore<T> implements IVectorStore<T> {

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final Class<T> clazz;

    public InMemoryVectorStore(InMemoryEmbeddingStore<TextSegment> store, Class<T> clazz) {
        this.store = store;
        this.clazz = clazz;
    }

    @Override
    public void add(float[] vector, T t) {
        store.add(new Embedding(vector), TextSegment.from(JsonUtils.toJson(t)));
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

    //-----to write and load contents from disk (unique to InMemoryVectorStore)-----------------------------------------

    public void write(IDatastore datastore, String vectorStoreLocation) {
        datastore.write(vectorStoreLocation, compress(store.serializeToJson()));
    }

    public static<T> InMemoryVectorStore<T> load(IDatastore datastore, String sourceManifestId, Class<T> clazz) {
        byte[] vectorStoreJsonGzBytes = datastore.read(vectorStoreLocation(sourceManifestId));
        String vectorStoreJson = decompress(vectorStoreJsonGzBytes);
        InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromJson(vectorStoreJson);
        return new InMemoryVectorStore<T>(store, clazz);
    }

    private static byte[] compress(String value) {
        try {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
                gzip.finish();
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String decompress(byte[] compressed) {
        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
