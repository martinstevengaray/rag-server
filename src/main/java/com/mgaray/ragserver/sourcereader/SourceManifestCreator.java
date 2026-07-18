package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceManifestCreator {

    private final DataFetcher dataFetcher;

    public SourceManifestCreator(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

//    public Models.SourceManifest create(String sourceManifestId) {
//        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
//        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
//            String inputSourceRecordId = String.format("%02d", recordNumber);
//            String inputRecordLocation = downloadsFolder + "/title-" + inputSourceRecordId + ".json";
//            String inputTextLocation = downloadsFolder + "/title-" + inputSourceRecordId + ".txt";
//            if (FileUtils.exists(inputRecordLocation) && FileUtils.exists(inputTextLocation)) {
//                String text = FileUtils.readFile(inputTextLocation);
//                //copy source.text file
//                String sourceRecordId = "title" + inputSourceRecordId;
//                String textLocation = Models.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
//                String chunkManifestLocation = Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
//                dataFetcher.save(textLocation, text);
//                //save source.json file
//                Map<String, Object> record = FileUtils.readJsonFile(inputRecordLocation);
//                Models.SourceRecord sourceRecord = new Models.SourceRecord(
//                        sourceRecordId,
//                        record.get("source_url").toString(),
//                        record.get("retrieved_at").toString(),
//                        record.get("title").toString(),
//                        textLocation,
//                        chunkManifestLocation);
//                sourceRecords.add(sourceRecord);
//            }
//        }
//        return new Models.SourceManifest(sourceManifestId, sourceRecords, new ArrayList<>());
//    }
}
