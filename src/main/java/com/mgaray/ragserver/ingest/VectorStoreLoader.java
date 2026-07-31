package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.ChunkManifest;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.Models.IngestionConfig;
import com.mgaray.ragserver.storage.vector.IVectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VectorStoreLoader {

    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;
    private final ExecutorService executor;

    public VectorStoreLoader(IDatastore datastore, IVectorStore<Chunk> vectorStore, IngestionConfig ingestionConfig) {
        this.datastore = datastore;
        this.vectorStore = vectorStore;
        this.executor = Executors.newFixedThreadPool(ingestionConfig.numberOfEmbeddingThreads());
    }

    public void load(IngestionManifest ingestionManifest, SourceRecordsDocument sourceRecordsDocument) {
        if (vectorStore.resultsExist(datastore, ingestionManifest.vectorStoreSpec())) {
            return;
        }
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (SourceRecord sourceRecord : sourceRecordsDocument.sourceRecords()) {
                futures.add(executor.submit(() -> load(sourceRecord)));
            }
            for (Future<?> future : futures) {
                future.get(); //blocks until done
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
        vectorStore.writeResults(datastore, ingestionManifest.vectorStoreSpec());
    }

    private void load(SourceRecord sourceRecord) {
        String chunkManifestLocation = sourceRecord.chunkManifestLocation();
        ChunkManifest chunkManifest = datastore.readObject(chunkManifestLocation, ChunkManifest.class);
        for (Chunk chunk : chunkManifest.chunks()) {
            if (!vectorStore.exists(chunk)) {
                String embeddingLocation = chunk.embeddingLocation();
                float[] vector = datastore.readFloatArray(embeddingLocation);
                vectorStore.add(vector, chunk);
            }
        }
    }

}
