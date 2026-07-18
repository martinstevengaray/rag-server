package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;
import com.mgaray.ragserver.chunker.LocationConventions;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceReader { //todo rename to sourceTransformer or sourceLoader

    private final DataFetcher dataFetcher;

    public SourceReader(DataFetcher dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public Models.SourceManifest sourceFolderForPortland(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String inputSourceRecordId = String.format("%02d", recordNumber);
            String inputRecordLocation = downloadsFolder + "/title-" + inputSourceRecordId + ".json";
            String inputTextLocation = downloadsFolder + "/title-" + inputSourceRecordId + ".txt";
            if (FileUtils.exists(inputRecordLocation) && FileUtils.exists(inputTextLocation)) {
                String text = FileUtils.readFile(inputTextLocation);
                //copy source.text file
                String sourceRecordId = "title" + inputSourceRecordId;
                String textLocation = LocationConventions.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
                dataFetcher.save(textLocation, text);
                //save source.json file
                Map<String, Object> record = FileUtils.readJsonFile(inputRecordLocation);
                Models.SourceRecord sourceRecord = new Models.SourceRecord(
                        sourceRecordId,
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        textLocation);
                sourceRecords.add(sourceRecord);
            }
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords, new HashMap<>());
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public Models.SourceManifest sourceFolderForOregon(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = FileUtils.readJsonlFile(downloadsFolder + "/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String inputSourceRecordId = record.get("chapter").toString();
            if (inputSourceRecordId.length() == 1) { inputSourceRecordId = "00" + inputSourceRecordId; }
            else if (inputSourceRecordId.length() == 2) { inputSourceRecordId = "0" + inputSourceRecordId; }
            String inputTextLocation = downloadsFolder + "/text/ors" + inputSourceRecordId + ".txt";
            String text = FileUtils.readFile(inputTextLocation);
            //save source.text file
            String sourceRecordId = "ors" + inputSourceRecordId;
            String textLocation  = LocationConventions.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
            dataFetcher.save(textLocation, text);
            //save source.json file
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    textLocation);
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords, new HashMap<>());
    }

    public Models.SourceManifest sourceFolderForNabAndWebc(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = FileUtils.readJsonlFile(downloadsFolder + "/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String text = record.get("text").toString();
            //save sourceRecord.text file
            String textLocation = LocationConventions.sourceRecordTextLocation(sourceManifestId, sourceRecordId);
            dataFetcher.save(textLocation, text);
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    textLocation);
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords, new HashMap<>());
    }

}
