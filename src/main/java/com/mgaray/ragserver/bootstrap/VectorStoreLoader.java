package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.ChunkManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.SourceRecordsDocument;
import com.mgaray.ragserver.vectorstore.IVectorStore;

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
                    float[] vector = datastore.readEmbedding(embeddingLocation);
                    vectorStore.add(vector, chunk);
                }
            }
        }
        vectorStore.writeResults(datastore, ingestionManifest.vectorStoreSpec());
    }

}
