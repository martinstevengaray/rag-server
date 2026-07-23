package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.ChunkManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.ChunkMatch;

import java.util.ArrayList;
import java.util.List;

import static com.mgaray.ragserver.common.Models.vectorStoreLocation;

public class VectorStore {

    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;

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

    public List<ChunkMatch> get(float[] searchVector, int topK) {
        List<IVectorStore.VectorRecord<Chunk>> results =  vectorStore.get(searchVector, topK, Chunk.class); //todo Chunk.class remove
        List<ChunkMatch> chunkMatches = new ArrayList<>();
        for (IVectorStore.VectorRecord<Chunk> match : results) {
            double matchScore = match.matchScore();
            chunkMatches.add(new ChunkMatch(match.t(), matchScore));
        }
        return chunkMatches;
    }

}
