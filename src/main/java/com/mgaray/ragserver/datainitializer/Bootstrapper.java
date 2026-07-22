package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Chunker;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ModelType;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;

import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class Bootstrapper {

    private final IDatastore sourceDatastore;
    private final IDatastore outDatastore;

    public Bootstrapper(IDatastore sourceDatastore,
                        IDatastore outDatastore) {
        this.sourceDatastore = sourceDatastore;
        this.outDatastore = outDatastore;
    }

    public void bootstrap(String sourceCatalogLocation, String ingestManifestId) {
        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(500, 0.5f),
                new EmbeddingSpec(ModelType.BGE_SMALL_EN_V15_QUANTIZED));
        SourceCatalog sourceCatalog = sourceDatastore.readObject(sourceCatalogLocation, SourceCatalog.class);

        DataInitializer dataInitializer = new DataInitializer(sourceDatastore, outDatastore);
        List<String> errors = dataInitializer.create(sourceCatalog, ingestManifestId, runDefinition);

        String ingestManifestLocation = ingestManifestLocation(ingestManifestId);
        IngestionManifest ingestionManifest = outDatastore.readObject(ingestManifestLocation, IngestionManifest.class);

        Chunker chunker = new Chunker(outDatastore);
        chunker.chunk(ingestionManifest);

        Embedder embedder = new Embedder(outDatastore);
        embedder.embed(ingestionManifest);

        VectorStore vectorStore = new VectorStore(outDatastore);
        vectorStore.load(ingestionManifest);

        System.out.println(ingestManifestId + " sourceRecords: " + ingestionManifest.sourceRecords().size() + ". errors: " + errors);
    }

}
