package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.ModelRecords;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceReader {

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt
    public ModelRecords.SourceRecords sourceFolderForPortland(String downloadsFolder) {
        List<ModelRecords.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String recordNumberString = String.format("%02d", recordNumber);
            String recordFile = downloadsFolder + "/title-" + recordNumberString + ".json";
            String textFile = downloadsFolder + "/title-" + recordNumberString + ".txt";
            if (FileUtils.exists(recordFile) && FileUtils.exists(textFile)) {
                Map<String, Object> record = FileUtils.readJsonFile(recordFile);
                ModelRecords.SourceRecord sourceRecord = new ModelRecords.SourceRecord(
                        Integer.toString(recordNumber),
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        new ModelRecords.Resource(null, textFile));
                sourceRecords.add(sourceRecord);
            }
        }
        return new ModelRecords.SourceRecords(sourceRecords);
    }

    //ors001.txt - ors838.txt
    public ModelRecords.SourceRecords sourceFolderForOregon(String downloadsFolder) {
        List<ModelRecords.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = FileUtils.readJsonlFile(downloadsFolder + "/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String recordKey = record.get("chapter").toString();
            String textFile = downloadsFolder + "/text/ors" + recordKey + ".txt";
            ModelRecords.SourceRecord sourceRecord = new ModelRecords.SourceRecord(
                    recordKey,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    new ModelRecords.Resource(null, textFile));
            sourceRecords.add(sourceRecord);
        }
        return new ModelRecords.SourceRecords(sourceRecords);
    }

    public ModelRecords.SourceRecords sourceFolderForNabAndWebc(String downloadsFolder) {
        List<ModelRecords.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = FileUtils.readJsonlFile(downloadsFolder + "/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String recordKey = record.get("id").toString();
            String textFile = downloadsFolder + "/text/" + recordKey + ".txt";
            String textFileContents = record.get("text").toString();
            if (!FileUtils.exists(textFile)) {
                FileUtils.writeFile(textFile, textFileContents);
            }
            ModelRecords.SourceRecord sourceRecord = new ModelRecords.SourceRecord(
                    recordKey,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    new ModelRecords.Resource(null, textFile));
            sourceRecords.add(sourceRecord);
        }
        return new ModelRecords.SourceRecords(sourceRecords);
    }

}
