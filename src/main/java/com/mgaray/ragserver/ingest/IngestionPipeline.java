package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.logger.ILogger;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.storage.vector.IVectorStore;

import java.time.Duration;
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
                    RunDefinition runDefinition,
                    ILogger logger) {
        // Start
        long tick = System.currentTimeMillis();

        // SourceCatalogValidator
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);
        List<String> errors = SourceCatalogValidator.validate(sourceCatalog);
        if (!errors.isEmpty()) {
            logger.error("Source catalog '" + sourceCatalogLocation + "' errors: " + errors);
            return;
        }

        // ManifestBuilder
        logger.log("ManifestBuilder");
        ManifestBuilder manifestBuilder = new ManifestBuilder(sourceDatastore, ingestionDatastore);
        IngestionManifest ingestionManifest = manifestBuilder.create(sourceCatalog, ingestionManifestId, runDefinition);
        SourceRecordsDocument sourceRecordsDocument = ingestionDatastore.readObject
                (ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);

        // Chunker
        logger.log("Chunker");
        Chunker chunker = new Chunker(ingestionDatastore);
        chunker.chunk(ingestionManifest, sourceRecordsDocument);

        // Embedder
        logger.log("Embedder");
        Embedder embedder = new Embedder(ingestionDatastore, ingestionConfig);
        embedder.embed(ingestionManifest, sourceRecordsDocument);

        // VectorStoreLoader
        logger.log("VectorStoreLoader");
        for (IVectorStore<Chunk> vectorStore : vectorStores) {
            vectorStore.initialize(runDefinition.embeddingSpec());
            VectorStoreLoader vectorStoreLoader =
                    new VectorStoreLoader(ingestionDatastore, vectorStore, ingestionConfig);
            vectorStoreLoader.load(ingestionManifest, sourceRecordsDocument);
        }

        // Finish
        long elapsedTime = System.currentTimeMillis() - tick;
        logger.log(ingestionManifestId + " complete in " + formatElapsedTime(elapsedTime));
    }

    private static String formatElapsedTime(long elapsedMillis) {
        Duration duration = Duration.ofMillis(elapsedMillis);
        return String.format("%02d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

}
