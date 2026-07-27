package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.ModelValidator;
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
    private final IVectorStore<Chunk> outVectorStore;
    private final ModelValidator modelValidator = new ModelValidator();

    public Bootstrapper(BootstrapperConfig bootstrapperConfig,
                        IDatastore sourceDatastore,
                        IDatastore ingestionDatastore,
                        IVectorStore<Chunk> outVectorStore) {
        this.bootstrapperConfig = bootstrapperConfig;
        this.sourceDatastore = sourceDatastore;
        this.ingestionDatastore = ingestionDatastore;
        this.outVectorStore = outVectorStore;
    }

    public void bootstrap(String sourceCatalogLocation,
                          String ingestManifestId,
                          RunDefinition runDefinition) {
        // SourceCatalog
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);

        // DataInitializer
        System.out.println("DataInitializer");
        DataInitializer dataInitializer = new DataInitializer(sourceDatastore, ingestionDatastore);
        IngestionManifest ingestionManifest = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);
        SourceRecordsDocument sourceRecordsDocument = ingestionDatastore.readObject
                (ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);
        List<String> errors = modelValidator.validate(ingestionManifest, sourceRecordsDocument);
        if (!errors.isEmpty()) {
            System.out.println(ingestManifestId + "errors: " + errors);
            return;
        }

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
        outVectorStore.initialize(runDefinition.embeddingSpec());
        VectorStoreLoader vectorStoreLoader = new VectorStoreLoader(ingestionDatastore, outVectorStore);
        vectorStoreLoader.load(ingestionManifest, sourceRecordsDocument);

        // Complete
        System.out.println(ingestManifestId + " complete");
    }

}
