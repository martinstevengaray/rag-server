package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.Models.SourceRecordsDocument;

import java.util.ArrayList;
import java.util.List;

public class DataInitializer {

    private final IDatastore sourceDatastore;
    private final IDatastore ingestionDatastore;

    public DataInitializer(IDatastore sourceDatastore, IDatastore ingestionDatastore) {
        this.sourceDatastore = sourceDatastore;
        this.ingestionDatastore = ingestionDatastore;
    }

    public IngestionManifest create(SourceCatalog sourceCatalog, String ingestManifestId, RunDefinition runDefinition) {
        List<SourceRecord> sourceRecords = new ArrayList<>();
        for (Source source : sourceCatalog.sources()) {
            String sourceRecordId = source.id();
            String inputTextLocation = source.location();
            String textLocation = sourceRecordTextLocation(ingestManifestId, sourceRecordId);
            String chunkManifestLocation = chunkManifestLocation(ingestManifestId, sourceRecordId);
            if (!ingestionDatastore.exists(textLocation)) { // copy source text if not already done so
                ingestionDatastore.writeString(textLocation, sourceDatastore.readString(inputTextLocation));
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
        SourceRecordsDocument sourceRecordsDocument = new SourceRecordsDocument(sourceRecords);
        String sourceRecordsLocation = sourceRecordsDocumentLocation(ingestManifestId);
        if (!ingestionDatastore.exists(sourceRecordsLocation)) {
            ingestionDatastore.writeObject(sourceRecordsLocation, sourceRecordsDocument);
        }

        VectorStoreSpec vectorStoreSpec = new VectorStoreSpec(inMemoryVectorStoreExportLocation(ingestManifestId),
                                                              s3VectorStoreManifestLocation(ingestManifestId));
        IngestionManifest ingestionManifest =
                new IngestionManifest(ingestManifestId, runDefinition, sourceRecordsLocation, vectorStoreSpec);
        ingestionDatastore.writeIngestionManifest(ingestionManifest);
        return ingestionManifest;
    }


    private static String sourceRecordsDocumentLocation(String sourceManifestId) {
        return sourceManifestId + "/sourceRecordsDocument.json";
    }

    private static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }

    private static String chunkManifestLocation(String sourceManifestId, String sourceRecordId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

    private static String inMemoryVectorStoreExportLocation(String sourceManifestId) {
        return sourceManifestId + "/vectorStore.json.gz";
    }

    private static String s3VectorStoreManifestLocation(String sourceManifestId) {
        return sourceManifestId + "/s3VectorStore.json";
    }

}
