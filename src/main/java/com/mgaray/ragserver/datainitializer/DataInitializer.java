package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;

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

    public List<String> create(Models.SourceManifest inputSourceManifest, Models.RunDefinition runDefinition) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        String sourceManifestId = inputSourceManifest.id();
        for (Models.SourceRecord inputSourceRecord : inputSourceManifest.sourceRecords()) {
            String sourceRecordId = inputSourceRecord.id();
            String inputTextLocation = inputSourceRecord.textLocation();
            String textLocation = Models.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            if (!outputDataStore.exists(textLocation)) { // copy source text if not already done so
                outputDataStore.save(textLocation, inputDataStore.fetch(inputTextLocation));
            }
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    inputSourceRecord.sourceUrl(),
                    inputSourceRecord.retrievedAt(),
                    inputSourceRecord.title(),
                    textLocation,
                    chunkManifestLocation);
            sourceRecords.add(sourceRecord);
        }
        Models.SourceManifest outputSourceManifest = new Models.SourceManifest(sourceManifestId, runDefinition, sourceRecords, new ArrayList<>());
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        outputDataStore.save(sourceManifestLocation, outputSourceManifest);
        return sourceValidator.validate(outputSourceManifest);
    }

}
