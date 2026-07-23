package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.ModelValidator;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.Source;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.SourceRecord;

import java.util.ArrayList;
import java.util.List;

import static com.mgaray.ragserver.common.Models.chunkManifestLocation;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;
import static com.mgaray.ragserver.common.Models.sourceRecordTextLocation;
import static com.mgaray.ragserver.common.Models.vectorStoreLocation;

public class DataInitializer {

    private final ModelValidator modelValidator = new ModelValidator();
    private final IDatastore sourceDatastore;
    private final IDatastore outDatastore;

    public DataInitializer(IDatastore sourceDatastore, IDatastore outDatastore) {
        this.sourceDatastore = sourceDatastore;
        this.outDatastore = outDatastore;
    }

    public List<String> create(SourceCatalog sourceCatalog, String ingestManifestId, RunDefinition runDefinition) {
        List<SourceRecord> sourceRecords = new ArrayList<>();
        for (Source source : sourceCatalog.sources()) {
            String sourceRecordId = source.id();
            String inputTextLocation = source.location();
            String textLocation = sourceRecordTextLocation(ingestManifestId, sourceRecordId);
            String chunkManifestLocation = chunkManifestLocation(ingestManifestId, sourceRecordId);
            if (!outDatastore.exists(textLocation)) { // copy source text if not already done so
                outDatastore.writeString(textLocation, sourceDatastore.readString(inputTextLocation));
            }
            SourceRecord sourceRecord = new SourceRecord(
                    sourceRecordId,
                    source.sourceUrl(),
                    source.retrievedAt(),
                    source.title(),
                    textLocation,
                    chunkManifestLocation);
            sourceRecords.add(sourceRecord);
        }
        String vectorStoreLocation = vectorStoreLocation(ingestManifestId);
        IngestionManifest ingestionManifest =
                new IngestionManifest(ingestManifestId, runDefinition, sourceRecords, vectorStoreLocation);
        String ingestManifestLocation = ingestManifestLocation(ingestManifestId);
        outDatastore.writeObject(ingestManifestLocation, ingestionManifest);
        return modelValidator.validate(ingestionManifest);
    }

}
