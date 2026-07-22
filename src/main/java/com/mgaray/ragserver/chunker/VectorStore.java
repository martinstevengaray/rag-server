package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.common.JsonUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import java.util.ArrayList;
import java.util.List;

public class VectorStore {

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final IDatastore datastore;


    public static VectorStore load(IDatastore datastore, String sourceManifestId) {
        byte[] vectorStoreJsonGzBytes = datastore.read(Models.vectorStoreLocation(sourceManifestId));
        String vectorStoreJson = decompress(vectorStoreJsonGzBytes);
        InMemoryEmbeddingStore<TextSegment> vectorStore = InMemoryEmbeddingStore.fromJson(vectorStoreJson);
        return new VectorStore(datastore, vectorStore);
    }

    public VectorStore(IDatastore datastore) {
        this(datastore, new InMemoryEmbeddingStore<>());
    }

    private VectorStore(IDatastore datastore, InMemoryEmbeddingStore<TextSegment> store) {
        this.datastore = datastore;
        this.store = store;
    }

    public void load(Models.IngestionManifest ingestionManifest) {
        for (Models.SourceRecord sourceRecord : ingestionManifest.sourceRecords()) {
            String chunkManifestLocation = sourceRecord.chunkManifestLocation();
            Models.ChunkManifest chunkManifest = datastore.readObject(chunkManifestLocation, Models.ChunkManifest.class);
            for (Models.Chunk chunk : chunkManifest.chunks()) {
                String embeddingLocation = chunk.embeddingLocation();
                float[] vector = datastore.readEmbedding(embeddingLocation);
                add(vector, chunk);
            }
        }
        String vectorStoreLocation = ingestionManifest.vectorStoreLocation();
        datastore.write(vectorStoreLocation, compress(store.serializeToJson()));
    }

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

//    public void save(String sourceManifestId) {
//        String location = Models.vectorStore(sourceManifestId);
//        String storeJson = store.serializeToJson();
//        datastore.write(location, compress(storeJson));
//    }

    public static byte[] compress(String value) {
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

    public static String decompress(byte[] compressed) {
        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
