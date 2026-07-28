package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.storage.vector.IVectorStore;

import java.util.List;

public class IngestionPipeline {

    private final Models.IngestionConfig ingestionConfig;
    private final IDatastore sourceDatastore;
    private final IDatastore ingestionDatastore;
    private final IVectorStore<Chunk>[] vectorStores;

    public IngestionPipeline(Models.IngestionConfig ingestionConfig,
                             IDatastore sourceDatastore,
                             IDatastore ingestionDatastore,
                             IVectorStore<Chunk>... vectorStores) {
        this.ingestionConfig = ingestionConfig;
        this.sourceDatastore = sourceDatastore;
        this.ingestionDatastore = ingestionDatastore;
        this.vectorStores = vectorStores;
    }

    public void run(String sourceCatalogLocation,
                    String ingestionManifestId,
                    RunDefinition runDefinition) {
        // Start
        long tick = System.currentTimeMillis();

        // SourceCatalogValidator
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);
        List<String> errors = SourceCatalogValidator.validateSourceCatalog(sourceCatalog);
        if (!errors.isEmpty()) {
            System.out.println("source catalog errors with ingestionManifestId: " + ingestionManifestId +
                    ", errors: " + errors);
            return;
        }

        // DataInitializer
        System.out.println("DataInitializer");
        DataInitializer dataInitializer = new DataInitializer(sourceDatastore, ingestionDatastore);
        IngestionManifest ingestionManifest = dataInitializer.create(sourceCatalog, ingestionManifestId, runDefinition);
        SourceRecordsDocument sourceRecordsDocument = ingestionDatastore.readObject
                (ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);

        // Chunker
        System.out.println("Chunker");
        Chunker chunker = new Chunker(ingestionDatastore);
        chunker.chunk(ingestionManifest, sourceRecordsDocument);

        // Embedder
        System.out.println("Embedder");
        Embedder embedder = new Embedder(ingestionDatastore, ingestionConfig);
        embedder.embed(ingestionManifest, sourceRecordsDocument);

        // VectorStoreLoader
        System.out.println("VectorStoreLoader");
        for (IVectorStore<Chunk> vectorStore : vectorStores) {
            vectorStore.initialize(runDefinition.embeddingSpec());
            VectorStoreLoader vectorStoreLoader = new VectorStoreLoader(ingestionDatastore, vectorStore);
            vectorStoreLoader.load(ingestionManifest, sourceRecordsDocument);
        }

        // Finish
        long elapsedTime = System.currentTimeMillis() - tick;
        System.out.println(ingestionManifestId + " complete in " + elapsedTime / 60000L + " minutes");
    }

}
