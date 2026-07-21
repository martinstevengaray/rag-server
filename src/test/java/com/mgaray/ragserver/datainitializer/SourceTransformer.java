package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceTransformer {

    private final IDatastore inputDataStore;
    private final IDatastore outputDataStore;
    private final SourceValidator sourceValidator = new SourceValidator();

    public SourceTransformer(IDatastore inputDataStore, IDatastore outputDataStore) {
        this.inputDataStore = inputDataStore;
        this.outputDataStore = outputDataStore;
    }

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public List<String> sourceFolderForPortland(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String inputSourceRecordId = String.format("%02d", recordNumber);
            String inputRecordLocation =  "/title-" + inputSourceRecordId + ".json";
            String inputTextLocation = "/title-" + inputSourceRecordId + ".txt";
            if (inputDataStore.exists(inputRecordLocation) && inputDataStore.exists(inputTextLocation)) {
                String text = inputDataStore.readString(inputTextLocation);
                String sourceRecordId = "title" + inputSourceRecordId;
                String textLocation = originalSourceTextLocation(sourceManifestId, sourceRecordId);
                String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
                outputDataStore.writeString(textLocation, text);
                Map<String, Object> record = inputDataStore.readJson(inputRecordLocation);
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
        outputDataStore.writeObject(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public List<String> sourceFolderForOregon(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = inputDataStore.readJsonl("/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String inputSourceRecordId = record.get("chapter").toString();
            if (inputSourceRecordId.length() == 1) { inputSourceRecordId = "00" + inputSourceRecordId; }
            else if (inputSourceRecordId.length() == 2) { inputSourceRecordId = "0" + inputSourceRecordId; }
            String inputTextLocation = "/text/ors" + inputSourceRecordId + ".txt";
            String text = inputDataStore.readString(inputTextLocation);
            String sourceRecordId = "ors" + inputSourceRecordId;
            String textLocation  = originalSourceTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            outputDataStore.writeString(textLocation, text);
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
        outputDataStore.writeObject(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    public List<String> sourceFolderForNabAndWebc(String sourceManifestId) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = inputDataStore.readJsonl("/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String text = record.get("text").toString();
            //save sourceRecord.text file
            String textLocation = originalSourceTextLocation(sourceManifestId, sourceRecordId);
            String chunkManifestLocation = null;//Models.chunkManifestLocation(sourceManifestId, sourceRecordId);
            outputDataStore.writeString(textLocation, text);
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
        outputDataStore.writeObject(sourceManifestLocation, sourceManifest);
        return sourceValidator.validate(sourceManifest);
    }

    private static String originalSourceManifestLocation(String sourceManifestId) {
        return "/" + sourceManifestId + "/sourceManifest.json";
    }

    private static String originalSourceTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sources/" + sourceRecordId + ".txt";
    }

}
