package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.common.Models;
import com.mgaray.ragserver.awsresources.DataFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceTransformer {

    private final DataFetcher inputDataFetcher;
    private final DataFetcher outputDataFetcher;
    private final SourceValidator sourceValidator = new SourceValidator();

    public SourceTransformer(DataFetcher inputDataFetcher, DataFetcher outputDataFetcher) {
        this.inputDataFetcher = inputDataFetcher;
        this.outputDataFetcher = outputDataFetcher;
    }

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public List<String> sourceFolderForPortland(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String inputSourceRecordId = String.format("%02d", recordNumber);
            String inputRecordLocation =  "/title-" + inputSourceRecordId + ".json";
            String inputTextLocation = "/title-" + inputSourceRecordId + ".txt";
            if (inputDataFetcher.exists(inputRecordLocation) && inputDataFetcher.exists(inputTextLocation)) {
                String text = inputDataFetcher.fetch(inputTextLocation);
                String sourceRecordId = "title" + inputSourceRecordId;
                String textLocation = originalSourceTextLocation(sourceManifestId, sourceRecordId);
                String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
                outputDataFetcher.save(textLocation, text);
                Map<String, Object> record = inputDataFetcher.fetchJson(inputRecordLocation);
                Models.SourceRecord sourceRecord = new Models.SourceRecord(
                        sourceRecordId,
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        textLocation,
                        chunkManifestLocation);
                sourceRecords.add(sourceRecord);
            }
        }
        Models.SourceManifest sourceManifest = new Models.SourceManifest(sourceManifestId, null, sourceRecords, null);
        String sourceManifestLocation = originalSourceManifestLocation(sourceManifestId);
        outputDataFetcher.save(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public List<String> sourceFolderForOregon(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = inputDataFetcher.fetchJsonl("/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String inputSourceRecordId = record.get("chapter").toString();
            if (inputSourceRecordId.length() == 1) { inputSourceRecordId = "00" + inputSourceRecordId; }
            else if (inputSourceRecordId.length() == 2) { inputSourceRecordId = "0" + inputSourceRecordId; }
            String inputTextLocation = "/text/ors" + inputSourceRecordId + ".txt";
            String text = inputDataFetcher.fetch(inputTextLocation);
            String sourceRecordId = "ors" + inputSourceRecordId;
            String textLocation  = originalSourceTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            outputDataFetcher.save(textLocation, text);
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    textLocation,
                    chunkManifestLocation);
            sourceRecords.add(sourceRecord);
        }
        Models.SourceManifest sourceManifest = new Models.SourceManifest(sourceManifestId, null, sourceRecords, null);
        String sourceManifestLocation = originalSourceManifestLocation(sourceManifestId);
        outputDataFetcher.save(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    public List<String> sourceFolderForNabAndWebc(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = inputDataFetcher.fetchJsonl("/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String text = record.get("text").toString();
            //save sourceRecord.text file
            String textLocation = originalSourceTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            outputDataFetcher.save(textLocation, text);
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    textLocation,
                    chunkManifestLocation);
            sourceRecords.add(sourceRecord);
        }
        Models.SourceManifest sourceManifest = new Models.SourceManifest(sourceManifestId, null, sourceRecords, null);
        String sourceManifestLocation = originalSourceManifestLocation(sourceManifestId);
        outputDataFetcher.save(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    private static String originalSourceManifestLocation(String sourceManifestId) {
        return "/" + sourceManifestId + "/sourceManifest.json";
    }

    private static String originalSourceTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sources/" + sourceRecordId + ".txt";
    }

}
