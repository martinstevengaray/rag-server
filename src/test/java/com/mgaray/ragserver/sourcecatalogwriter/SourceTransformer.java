package com.mgaray.ragserver.sourcecatalogwriter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.ingest.SourceCatalogValidator;
import com.mgaray.ragserver.util.JsonUtils;
import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class SourceTransformer {

    private final IDatastore inputDatastore;
    private final IDatastore outputDatastore;

    public SourceTransformer(IDatastore inputDatastore, IDatastore outputDatastore) {
        this.inputDatastore = inputDatastore;
        this.outputDatastore = outputDatastore;
    }

    //title-01.json - title-35.json  &&  title-01.txt - title-35.txt   (recall: 08 does not exit)
    public List<String> sourceFolderForPortland(String sourceCatalogId) {
        List<Source> sources = new ArrayList<>();
        for (int recordNumber = 1; recordNumber <= 35; recordNumber++) {
            String inputSourceRecordId = String.format("%02d", recordNumber);
            String inputRecordLocation =  "/title-" + inputSourceRecordId + ".json";
            String inputTextLocation = "/title-" + inputSourceRecordId + ".txt";
            if (inputDatastore.exists(inputRecordLocation) && inputDatastore.exists(inputTextLocation)) {
                String text = inputDatastore.readString(inputTextLocation);
                String sourceId = "title" + inputSourceRecordId;
                String textLocation = originalSourceTextLocation(sourceCatalogId, inputSourceRecordId);
                outputDatastore.writeString(textLocation, text);
                Map<String, Object> record = readJson(inputRecordLocation);
                Source source = new Source(
                        sourceId,
                        record.get("source_url").toString(),
                        record.get("retrieved_at").toString(),
                        record.get("title").toString(),
                        textLocation);
                sources.add(source);
            }
        }
        SourceCatalog sourceCatalog = new SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDatastore.writeObject(sourceCatalogLocation, sourceCatalog);
        return SourceCatalogValidator.validateSourceCatalog(sourceCatalog);
    }

    //ors001.txt - ors838.txt (recall: 627 exist in total)
    public List<String> sourceFolderForOregon(String sourceCatalogId) {
        List<Source> sources = new ArrayList<>();
        List<Map<String, Object>> manifest = readJsonl("/manifest.jsonl");
        for (Map<String, Object> record : manifest) {
            String inputSourceRecordId = record.get("chapter").toString();
            if (inputSourceRecordId.length() == 1) { inputSourceRecordId = "00" + inputSourceRecordId; }
            else if (inputSourceRecordId.length() == 2) { inputSourceRecordId = "0" + inputSourceRecordId; }
            String inputTextLocation = "/text/ors" + inputSourceRecordId + ".txt";
            String text = inputDatastore.readString(inputTextLocation);
            String sourceRecordId = "ors" + inputSourceRecordId;
            String textLocation  = originalSourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDatastore.writeString(textLocation, text);
            Source source = new Source(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("chapter_title").toString(),
                    textLocation);
            sources.add(source);
        }
        SourceCatalog sourceCatalog = new SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDatastore.writeObject(sourceCatalogLocation, sourceCatalog);
        return SourceCatalogValidator.validateSourceCatalog(sourceCatalog);
    }

    public List<String> sourceFolderForNabAndWebc(String sourceCatalogId) {
        List<Source> sources = new ArrayList<>();
        List<Map<String, Object>> chapters = readJsonl("/chapters.jsonl");
        for (Map<String, Object> record : chapters) {
            String sourceRecordId = record.get("id").toString();
            String text = record.get("text").toString();
            String textLocation = originalSourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDatastore.writeString(textLocation, text);
            Source source = new Source(
                    sourceRecordId,
                    record.get("source_url").toString(),
                    record.get("retrieved_at").toString(),
                    record.get("reference").toString(),
                    textLocation);
            sources.add(source);
        }
        SourceCatalog sourceCatalog = new SourceCatalog(sourceCatalogId, sources);
        String sourceCatalogLocation = sourceCatalogLocation(sourceCatalogId);
        outputDatastore.writeObject(sourceCatalogLocation, sourceCatalog);
        return SourceCatalogValidator.validateSourceCatalog(sourceCatalog);
    }

    private static String sourceCatalogLocation(String sourceCatalogId) {
        return sourceCatalogId + "/sourceCatalog.json";
    }

    private static String originalSourceTextLocation(String sourceManifestId, String sourceId) {
        return sourceManifestId + "/sources/" + sourceId + ".txt";
    }

    private Map<String, Object> readJson(String storageLocation) {
        String json = new String(inputDatastore.read(storageLocation), StandardCharsets.UTF_8);
        return JsonUtils.parse(json);
    }
    private List<Map<String, Object>> readJsonl(String storageLocation) {
        String json = new String(inputDatastore.read(storageLocation), StandardCharsets.UTF_8);
        return parseJsonl(json);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static List<Map<String, Object>> parseJsonl(String jsonl) {
        try (MappingIterator<Map<String, Object>> iterator = objectMapper
                .readerFor(new TypeReference<Map<String, Object>>() {})
                .readValues(jsonl)) {
            return iterator.readAll();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
