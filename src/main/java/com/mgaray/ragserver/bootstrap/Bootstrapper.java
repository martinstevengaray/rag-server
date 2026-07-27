package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.SourceRecordsDocument;
import com.mgaray.ragserver.vectorstore.IVectorStore;

import java.util.List;

public class Bootstrapper {

    private final BootstrapperConfig bootstrapperConfig;
    private final IDatastore sourceDatastore;
    private final IDatastore ingestionDatastore;
    private final IVectorStore<Chunk>[] vectorStores;

    public Bootstrapper(BootstrapperConfig bootstrapperConfig,
                        IDatastore sourceDatastore,
                        IDatastore ingestionDatastore,
                        IVectorStore<Chunk>... vectorStores) {
        this.bootstrapperConfig = bootstrapperConfig;
        this.sourceDatastore = sourceDatastore;
        this.ingestionDatastore = ingestionDatastore;
        this.vectorStores = vectorStores;
    }

    public void bootstrap(String sourceCatalogLocation,
                          String ingestionManifestId,
                          RunDefinition runDefinition) {
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
        Embedder embedder = new Embedder(ingestionDatastore, bootstrapperConfig);
        embedder.embed(ingestionManifest, sourceRecordsDocument);

        // VectorStoreLoader
        System.out.println("VectorStoreLoader");
        for (IVectorStore<Chunk> vectorStore : vectorStores) {
            vectorStore.initialize(runDefinition.embeddingSpec());
            VectorStoreLoader vectorStoreLoader = new VectorStoreLoader(ingestionDatastore, vectorStore);
            vectorStoreLoader.load(ingestionManifest, sourceRecordsDocument);
        }

        // Complete
        System.out.println(ingestionManifestId + " complete");
    }

}
