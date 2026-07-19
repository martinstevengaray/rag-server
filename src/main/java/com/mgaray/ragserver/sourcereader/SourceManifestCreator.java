package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//loads data from disk in expected format and creates a manifest to location further chunking, embeddings and vector store exports
public class SourceManifestCreator {

    private final DataFetcher dataFetcher;

    public SourceManifestCreator(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    public Models.SourceManifest create(String sourceManifestId) {
        String sourceRecordFolder = Models.sourceRecordFolder(sourceManifestId);
        List<String> sourceRecordIds = dataFetcher.list(sourceRecordFolder);
        //we only want source folders! not everything under the list
//        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
//        for (String sourceRecordId : sourceRecordIds) {
//            String textLocation = Models.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
//            String chunkManifestLocation = Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
//            //save source.json file
//            Map<String, Object> record = FileUtils.readJsonFile(inputRecordLocation);
//            Models.SourceRecord sourceRecord = new Models.SourceRecord(
//                    sourceRecordId,
//                    record.get("source_url").toString(),
//                    record.get("retrieved_at").toString(),
//                    record.get("title").toString(),
//                    textLocation,
//                    chunkManifestLocation);
//            sourceRecords.add(sourceRecord);
//        }
//        return new Models.SourceManifest(sourceManifestId, sourceRecords, new ArrayList<>());
        return null;
    }
}
