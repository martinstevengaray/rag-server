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

    public List<String> create(Models.SourceManifest inputSourceManifest, String outputSourceManifestId, Models.RunDefinition runDefinition) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (Models.SourceRecord inputSourceRecord : inputSourceManifest.sourceRecords()) {
            String sourceRecordId = inputSourceRecord.id();
            String inputTextLocation = inputSourceRecord.textLocation();
            String textLocation = Models.sourceRecordTextLocation(outputSourceManifestId, sourceRecordId);
            String chunkManifestLocation = Models.chunkManifestLocation(outputSourceManifestId, sourceRecordId);
            if (!outputDataStore.exists(textLocation)) { // copy source text if not already done so
                outputDataStore.writeString(textLocation, inputDataStore.readString(inputTextLocation));
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
        Models.SourceManifest outputSourceManifest = new Models.SourceManifest(outputSourceManifestId, runDefinition, sourceRecords, new ArrayList<>());
        String sourceManifestLocation = Models.sourceManifestLocation(outputSourceManifestId);
        outputDataStore.writeObject(sourceManifestLocation, outputSourceManifest);
        return sourceValidator.validate(outputSourceManifest);
    }

}
