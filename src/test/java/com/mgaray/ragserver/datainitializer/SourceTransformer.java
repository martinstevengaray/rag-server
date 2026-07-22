package com.mgaray.ragserver.datainitializer;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models;

import java.util.*;

public class SourceTransformer {

    private final IDatastore inputDataStore;
    private final IDatastore outputDataStore;
    private final SourceValidator sourceValidator = new SourceValidator();

    public SourceTransformer(IDatastore inputDataStore, IDatastore outputDataStore) {
        this.inputDataStore = inputDataStore;
        this.outputDataStore = outputDataStore;
    }

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public List<String> sourceFolderForPortland(String sourceCatalogId) {
        List<Models.Source> sources = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String inputSourceRecordId = String.format("%02d", recordNumber);
            String inputRecordLocation =  "/title-" + inputSourceRecordId + ".json";
            String inputTextLocation = "/title-" + inputSourceRecordId + ".txt";
            if (inputDataStore.exists(inputRecordLocation) && inputDataStore.exists(inputTextLocation)) {
                String text = inputDataStore.readString(inputTextLocation);
                String sourceId = "title" + inputSourceRecordId;
                String textLocation = originalSourceTextLocation(sourceCatalogId, inputSourceRecordId);
                outputDataStore.writeString(textLocation, text);
                Map<String, Object> record = inputDataStore.readJson(inputRecordLocation);
                Models.Source source = new Models.Source(
                        sourceId,
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        textLocation);
                sources.add(source);
            }
        }
        Models.SourceCatalog sourceCatalog = new Models.SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDataStore.writeObject(sourceCatalogLocation, sourceCatalog);
        return validate(sourceCatalog);
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public List<String> sourceFolderForOregon(String sourceCatalogId) {
        List<Models.Source> sources = new ArrayList<>();
        List<Map<String, Object>> manifest = inputDataStore.readJsonl("/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String inputSourceRecordId = record.get("chapter").toString();
            if (inputSourceRecordId.length() == 1) { inputSourceRecordId = "00" + inputSourceRecordId; }
            else if (inputSourceRecordId.length() == 2) { inputSourceRecordId = "0" + inputSourceRecordId; }
            String inputTextLocation = "/text/ors" + inputSourceRecordId + ".txt";
            String text = inputDataStore.readString(inputTextLocation);
            String sourceRecordId = "ors" + inputSourceRecordId;
            String textLocation  = originalSourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDataStore.writeString(textLocation, text);
            Models.Source source = new Models.Source(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    textLocation);
            sources.add(source);
        }
        Models.SourceCatalog sourceCatalog = new Models.SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDataStore.writeObject(sourceCatalogLocation, sourceCatalog);
        return validate(sourceCatalog);
    }

    public List<String> sourceFolderForNabAndWebc(String sourceCatalogId) {
        List<Models.Source> sources = new ArrayList<>();
        List<Map<String, Object>> chapters = inputDataStore.readJsonl("/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String text = record.get("text").toString();
            String textLocation = originalSourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDataStore.writeString(textLocation, text);
            Models.Source source = new Models.Source(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    textLocation);
            sources.add(source);
        }
        Models.SourceCatalog sourceCatalog = new Models.SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDataStore.writeObject(sourceCatalogLocation, sourceCatalog);
        return validate(sourceCatalog);
    }

    private static String sourceCatalogLocation(String sourceCatalogId) {
        return "/" + sourceCatalogId + "/sourceCatalog.json";
    }

    private static String originalSourceTextLocation(String sourceManifestId, String sourceId) {
        return "/" + sourceManifestId + "/sources/" + sourceId + ".txt";
    }

    private static List<String> validate(Models.SourceCatalog sourceCatalog) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (Models.Source source : sourceCatalog.sources()) {
            ids.add(source.id());
            sourceUrls.add(source.sourceUrl());
        }
        if (ids.size() != sourceCatalog.sources().size()) { //verify each source has a unique ids
            errors.add("ids.size() != source.sourceRecords().size() : " + ids.size() +" != " + sourceCatalog.sources().size());
        }
        if (sourceUrls.size() != sourceCatalog.sources().size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != source.sourceRecords().size() : " + sourceUrls.size() +" != " + sourceCatalog.sources().size());
        }
        System.out.println(sourceCatalog.title() + ": " + sourceCatalog.sources().size());
        return errors;
    }

}
