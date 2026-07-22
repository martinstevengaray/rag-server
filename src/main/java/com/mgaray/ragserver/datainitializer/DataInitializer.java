package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.common.Models.SourceCatalog;
import com.mgaray.ragserver.common.Models.Source;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.SourceRecord;

import java.util.ArrayList;
import java.util.List;

public class DataInitializer {

    private final SourceValidator sourceValidator = new SourceValidator();
    private final IDatastore inputDataStore;
    private final IDatastore outputDataStore;

    public DataInitializer(IDatastore inputDataStore, IDatastore outputDataStore) {
        this.inputDataStore = inputDataStore;
        this.outputDataStore = outputDataStore;
    }

    public List<String> create(SourceCatalog sourceCatalog, String ingestManifestId, RunDefinition runDefinition) {
        List<SourceRecord> sourceRecords = new ArrayList<>();
        for (Source source : sourceCatalog.sources()) {
            String sourceRecordId = source.id();
            String inputTextLocation = source.location();
            String textLocation = Models.sourceRecordTextLocation(ingestManifestId, sourceRecordId);
            String chunkManifestLocation = Models.chunkManifestLocation(ingestManifestId, sourceRecordId);
            if (!outputDataStore.exists(textLocation)) { // copy source text if not already done so
                outputDataStore.writeString(textLocation, inputDataStore.readString(inputTextLocation));
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
        String vectorStoreLocation = Models.vectorStoreLocation(ingestManifestId);
        IngestionManifest outputIngestionManifest =
                new IngestionManifest(ingestManifestId, runDefinition, sourceRecords, vectorStoreLocation);
        String sourceManifestLocation = Models.ingestManifestLocation(ingestManifestId);
        outputDataStore.writeObject(sourceManifestLocation, outputIngestionManifest);
        return sourceValidator.validate(outputIngestionManifest);
    }

}
