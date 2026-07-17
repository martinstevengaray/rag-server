package com.mgaray.ragserver.formatter.portland;

import com.mgaray.ragserver.ModelRecords;
import com.mgaray.ragserver.common.FileUtils;
import com.mgaray.ragserver.common.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        ModelRecords.Source source = null;

        String inputPortland = "../rag-content-corpus-download/src/portland_city_code/downloads-clean";
        source = (new Main()).sourceFolderForPortland(inputPortland);
        System.out.println(JsonUtils.toJsonPretty(source));

        String inputOregon = "../rag-content-corpus-download/src/oregon-state-code/downloads-clean";
        source = (new Main()).sourceFolderForOregon(inputOregon);
        System.out.println(JsonUtils.toJsonPretty(source));

        String inputWebc = "../rag-content-corpus-download/src/web-catholic-bible/downloads-clean";
        source = (new Main()).sourceFolderForNabAndWebc(inputWebc);
        System.out.println(JsonUtils.toJsonPretty(source));

        String inputNab = "../rag-content-corpus-download/src/new-american-bible/downloads-clean";
        source = (new Main()).sourceFolderForNabAndWebc(inputNab);
        System.out.println(JsonUtils.toJsonPretty(source));

    }

    //title-01.json - title-35.json
    //title-01.txt  - title-35.txt
    public ModelRecords.Source sourceFolderForPortland(String downloadsFolder) {
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
                        new ModelRecords.Resource(textFile));
                sourceRecords.add(sourceRecord);
            }
        }
        return new ModelRecords.Source(sourceRecords);
    }

    //ors001.txt - ors838.txt
    public ModelRecords.Source sourceFolderForOregon(String downloadsFolder) {
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
                    new ModelRecords.Resource(textFile));
            sourceRecords.add(sourceRecord);
        }
        return new ModelRecords.Source(sourceRecords);
    }

    public ModelRecords.Source sourceFolderForNabAndWebc(String downloadsFolder) {
        List<ModelRecords.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = FileUtils.readJsonlFile(downloadsFolder + "/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String recordKey = record.get("id").toString();
            String textFileContents = record.get("text").toString();
            ModelRecords.SourceRecord sourceRecord = new ModelRecords.SourceRecord(
                    recordKey,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    new ModelRecords.Resource(textFileContents)); //todo
            sourceRecords.add(sourceRecord);
        }
        return new ModelRecords.Source(sourceRecords);
    }

}
