package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.SourceCatalog;

import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class Bootstrapper {

    private final BootstrapperConfig config;
    private final IDatastore sourceDatastore;
    private final IDatastore outDatastore;

    public Bootstrapper(BootstrapperConfig config,
                        IDatastore sourceDatastore,
                        IDatastore outDatastore) {
        this.config = config;
        this.sourceDatastore = sourceDatastore;
        this.outDatastore = outDatastore;
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
        VectorStore vectorStore = new VectorStore(outDatastore);
        vectorStore.load(ingestionManifest);

        // Results
        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() +
                ", errors: " + errors);
    }

}
