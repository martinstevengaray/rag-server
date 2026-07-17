package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceReader {

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt
    public Models.SourceManifest sourceFolderForPortland(String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String recordNumberString = String.format("%02d", recordNumber);
            String recordFile = downloadsFolder + "/title-" + recordNumberString + ".json";
            String textFile = downloadsFolder + "/title-" + recordNumberString + ".txt";
            if (FileUtils.exists(recordFile) && FileUtils.exists(textFile)) {
                Map<String, Object> record = FileUtils.readJsonFile(recordFile);
                Models.SourceRecord sourceRecord = new Models.SourceRecord(
                        Integer.toString(recordNumber),
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        new Models.StorageLocation(null, textFile));
                sourceRecords.add(sourceRecord);
            }
        }
        return new Models.SourceManifest(sourceRecords);
    }

    //ors001.txt - ors838.txt
    public Models.SourceManifest sourceFolderForOregon(String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = FileUtils.readJsonlFile(downloadsFolder + "/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String recordId = record.get("chapter").toString();
            String textFile = downloadsFolder + "/text/ors" + recordId + ".txt";
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    recordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    new Models.StorageLocation(null, textFile));
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceRecords);
    }

    public Models.SourceManifest sourceFolderForNabAndWebc(String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = FileUtils.readJsonlFile(downloadsFolder + "/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String recordId = record.get("id").toString();
            String textFile = downloadsFolder + "/text/" + recordId + ".txt";
            String textFileContents = record.get("text").toString();
            if (!FileUtils.exists(textFile)) {
                FileUtils.writeFile(textFile, textFileContents);
            }
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    recordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    new Models.StorageLocation(null, textFile));
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceRecords);
    }

}
