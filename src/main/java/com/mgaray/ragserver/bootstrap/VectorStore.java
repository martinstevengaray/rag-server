package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.ChunkManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.ChunkMatch;
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

import static com.mgaray.ragserver.common.Models.vectorStoreLocation;

public class VectorStore {

//    private final InMemoryEmbeddingStore<TextSegment> store;
    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;


//    public static VectorStore load(IDatastore datastore, String sourceManifestId) {
//        byte[] vectorStoreJsonGzBytes = datastore.read(vectorStoreLocation(sourceManifestId));
//        String vectorStoreJson = decompress(vectorStoreJsonGzBytes);
//        InMemoryEmbeddingStore<TextSegment> vectorStore = InMemoryEmbeddingStore.fromJson(vectorStoreJson);
//        return new VectorStore(datastore, vectorStore);
//    }

    public VectorStore(IDatastore datastore) {
        this(datastore, new InMemoryVectorStore<>());
    }

    public VectorStore(IDatastore datastore, IVectorStore<Chunk> vectorStore) {
        this.datastore = datastore;
        this.vectorStore = vectorStore;
    }

    public void load(IngestionManifest ingestionManifest) {
        for (SourceRecord sourceRecord : ingestionManifest.sourceRecords()) {
            String chunkManifestLocation = sourceRecord.chunkManifestLocation();
            ChunkManifest chunkManifest = datastore.readObject(chunkManifestLocation, ChunkManifest.class);
            for (Chunk chunk : chunkManifest.chunks()) {
                String embeddingLocation = chunk.embeddingLocation();
                float[] vector = datastore.readEmbedding(embeddingLocation);
                vectorStore.add(vector, chunk);
            }
        }
        if (vectorStore instanceof InMemoryVectorStore) { //todo
            String vectorStoreLocation = ingestionManifest.vectorStoreLocation();
            ((InMemoryVectorStore<Chunk>)vectorStore).write(datastore, vectorStoreLocation);
        }
    }

//    public void add(float[] vector, Chunk chunk) {
//        store.add(new Embedding(vector), TextSegment.from(JsonUtils.toJson(chunk)));
//    }

    public List<ChunkMatch> get(float[] searchVector, int topK) {

        List<IVectorStore.VectorRecord<Chunk>> results =  vectorStore.get(searchVector, topK, Chunk.class); //todo Chunk.class remove

        List<ChunkMatch> chunkMatches = new ArrayList<>();
//        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
//                .queryEmbedding(new Embedding(searchVector))
//                .maxResults(count)
//                .build();
//        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        for (IVectorStore.VectorRecord<Chunk> match : results) {
//            Chunk chunk = JsonUtils.toObject(match.embedded().text(), Chunk.class);
            double matchScore = match.matchScore();
            chunkMatches.add(new ChunkMatch(match.t(), matchScore));
        }
        return chunkMatches;
    }

//    public static byte[] compress(String value) {
//        try {
//            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
//                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
//                gzip.write(value.getBytes(StandardCharsets.UTF_8));
//                gzip.finish();
//                return output.toByteArray();
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public static String decompress(byte[] compressed) {
//        try(GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
//            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

}
