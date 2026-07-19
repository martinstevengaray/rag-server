package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.ArrayList;
import java.util.List;

public class SourceManifestCreator { //todo rename to DataInitializer

    private final SourceValidator sourceValidator = new SourceValidator();
    private final DataFetcher inputDataFetcher;
    private final DataFetcher outputDataFetcher;

    public SourceManifestCreator(DataFetcher inputDataFetcher, DataFetcher outputDataFetcher) {
        this.inputDataFetcher = inputDataFetcher;
        this.outputDataFetcher = outputDataFetcher;
    }

    public List<String> create(String sourceManifestId) {
        return create(inputDataFetcher.fetch(Models.originalSourceManifestLocation(sourceManifestId), Models.SourceManifest.class));
    }

    public List<String> create(Models.SourceManifest inputSourceManifest) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        String sourceManifestId = inputSourceManifest.id();
        for (Models.SourceRecord inputSourceRecord : inputSourceManifest.sourceRecords()) {
            String sourceRecordId = inputSourceRecord.id();
            String inputTextLocation = inputSourceRecord.textLocation();
            String textLocation = Models.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            outputDataFetcher.save(textLocation, inputDataFetcher.fetch(inputTextLocation)); //copy source text
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    inputSourceRecord.sourceUrl(),
                    inputSourceRecord.retrievedAt(),
                    inputSourceRecord.title(),
                    textLocation,
                    chunkManifestLocation);
            sourceRecords.add(sourceRecord);
        }
        Models.SourceManifest outputSourceManifest = new Models.SourceManifest(sourceManifestId, sourceRecords, new ArrayList<>());
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        outputDataFetcher.save(sourceManifestLocation, outputSourceManifest);
        return sourceValidator.validate(outputSourceManifest);
    }
}
