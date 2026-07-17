package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceReader {

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public Models.SourceManifest sourceFolderForPortland(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String sourceRecordId = String.format("%02d", recordNumber);
            String recordFileLocation = downloadsFolder + "/title-" + sourceRecordId + ".json";
            String textLocation = downloadsFolder + "/title-" + sourceRecordId + ".txt";
            if (FileUtils.exists(recordFileLocation) && FileUtils.exists(textLocation)) {
                Map<String, Object> record = FileUtils.readJsonFile(recordFileLocation);
                Models.SourceRecord sourceRecord = new Models.SourceRecord(
                        sourceRecordId,
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        new Models.StorageLocation(null, textLocation),
                        new Models.StorageLocation(null, null), //does not yet exist, will be created after chunking
                        null,
                        null);
                sourceRecords.add(sourceRecord);
            }
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords);
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public Models.SourceManifest sourceFolderForOregon(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> manifest = FileUtils.readJsonlFile(downloadsFolder + "/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String sourceRecordId = record.get("chapter").toString();
            String textLocation = downloadsFolder + "/text/ors" + sourceRecordId + ".txt";
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    new Models.StorageLocation(null, textLocation),
                    new Models.StorageLocation(null, null), //does not yet exist, will be created after chunking
                    null,
                    null);
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords);
    }

    public Models.SourceManifest sourceFolderForNabAndWebc(String sourceManifestId, String downloadsFolder) {
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        List<Map<String, Object>> chapters = FileUtils.readJsonlFile(downloadsFolder + "/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String textLocation = downloadsFolder + "/text/" + sourceRecordId + ".txt"; //todo consider removing now that lazyText can be leveraged
            //source data cleanup hack: only needed to be run once after source download -todo move to download source repo (see related todo above)
            //if (!FileUtils.exists(textLocation)) {
            //    String textFileContents = record.get("text").toString();
            //    FileUtils.writeFile(textLocation, textFileContents);
            //}
            Models.SourceRecord sourceRecord = new Models.SourceRecord(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    new Models.StorageLocation(null, textLocation),
                    new Models.StorageLocation(null, null), //does not yet exist, will be created after chunking
                    record.get("text").toString(),
                    null);
            sourceRecords.add(sourceRecord);
        }
        return new Models.SourceManifest(sourceManifestId, sourceRecords);
    }

}
