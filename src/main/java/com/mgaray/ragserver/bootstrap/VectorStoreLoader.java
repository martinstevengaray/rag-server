package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.ChunkManifest;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.storage.vector.IVectorStore;

public class VectorStoreLoader {

    private final IDatastore datastore;
    private final IVectorStore<Chunk> vectorStore;

    public VectorStoreLoader(IDatastore datastore, IVectorStore<Chunk> vectorStore) {
        this.datastore = datastore;
        this.vectorStore = vectorStore;
    }

    public void load(IngestionManifest ingestionManifest, SourceRecordsDocument sourceRecordsDocument) {
        if (vectorStore.resultsExist(datastore, ingestionManifest.vectorStoreSpec())) {
            return;
        }
        for (SourceRecord sourceRecord : sourceRecordsDocument.sourceRecords()) {
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
        vectorStore.writeResults(datastore, ingestionManifest.vectorStoreSpec());
    }

}
