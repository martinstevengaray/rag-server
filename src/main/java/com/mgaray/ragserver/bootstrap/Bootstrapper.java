package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.BootstrapperConfig;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class Bootstrapper {

    private final BootstrapperConfig bootstrapperConfig;
    private final IDatastore sourceDatastore;
    private final IDatastore outDatastore;
    private final IVectorStore<Chunk> outVectorStore;

    public Bootstrapper(BootstrapperConfig bootstrapperConfig,
                        IDatastore sourceDatastore,
                        IDatastore outDatastore,
                        IVectorStore<Chunk> outVectorStore) {
        this.bootstrapperConfig = bootstrapperConfig;
        this.sourceDatastore = sourceDatastore;
        this.outDatastore = outDatastore;
        this.outVectorStore = outVectorStore;
    }

    public void bootstrap(String sourceCatalogLocation,
                          String ingestManifestId,
                          RunDefinition runDefinition) {
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);
        // DataInitializer
        System.out.println("DataInitializer");
        DataInitializer dataInitializer = new DataInitializer(sourceDatastore, outDatastore);
        List<String> errors = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);
        String ingestManifestLocation = ingestManifestLocation(ingestManifestId);
        IngestionManifest ingestionManifest = outDatastore.readObject(ingestManifestLocation, IngestionManifest.class);

        // Chunker
        System.out.println("Chunker");
        Chunker chunker = new Chunker(outDatastore);
        chunker.chunk(ingestionManifest);

        // Embedder
        System.out.println("Embedder");
        Embedder embedder = new Embedder(outDatastore, bootstrapperConfig);
        embedder.embed(ingestionManifest);

        // VectorStoreLoader
        System.out.println("VectorStoreLoader");
        if (outVectorStore instanceof S3VectorStore<Chunk>) {
            initializeS3VectorStore(runDefinition, (S3VectorStore<Chunk>)outVectorStore);
        }
        VectorStoreLoader vectorStoreLoader = new VectorStoreLoader(outDatastore, outVectorStore);
        vectorStoreLoader.load(ingestionManifest);

        // Results
        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() +
                ", errors: " + errors);
    }

    private void initializeS3VectorStore(RunDefinition runDefinition, S3VectorStore<Chunk> s3VectorStore) {
        final EmbeddingSpec embeddingSpec = runDefinition.embeddingSpec();
        final EmbeddingModel embeddingModel = Embedder.createEmbeddingModel(embeddingSpec, null);
        int modelDimension = embeddingModel.dimension();
        System.out.println("modelDimension = " + modelDimension);
        s3VectorStore.ensureIndex(modelDimension);
    }

}
