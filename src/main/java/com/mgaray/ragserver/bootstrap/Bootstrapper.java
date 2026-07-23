package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.Chunk;

import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class Bootstrapper {

    private final BootstrapperConfig config;
    private final IDatastore sourceDatastore;
    private final IDatastore outDatastore;
    private final IVectorStore<Chunk> outVectorStore;

    public Bootstrapper(BootstrapperConfig config,
                        IDatastore sourceDatastore,
                        IDatastore outDatastore,
                        IVectorStore<Chunk> outVectorStore) {
        this.config = config;
        this.sourceDatastore = sourceDatastore;
        this.outDatastore = outDatastore;
        this.outVectorStore = outVectorStore;
    }

    public void bootstrap(String sourceCatalogLocation,
                          String ingestManifestId,
                          RunDefinition runDefinition) {
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);

        // DataInitializer
        DataInitializer dataInitializer = new DataInitializer(sourceDatastore, outDatastore);
        List<String> errors = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);
        String ingestManifestLocation = ingestManifestLocation(ingestManifestId);
        IngestionManifest ingestionManifest = outDatastore.readObject(ingestManifestLocation, IngestionManifest.class);

        // Chunker
        Chunker chunker = new Chunker(outDatastore);
        chunker.chunk(ingestionManifest);

        // Embedder
        Embedder embedder = new Embedder(outDatastore, config);
        embedder.embed(ingestionManifest);

        // VectorStore
        VectorStoreDelegate vectorStoreDelegate = new VectorStoreDelegate(outDatastore, outVectorStore);
        vectorStoreDelegate.load(ingestionManifest);

        // Results
        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() +
                ", errors: " + errors);
    }

}
