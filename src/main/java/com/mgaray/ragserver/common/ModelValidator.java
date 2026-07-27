package com.mgaray.ragserver.common;

import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.SourceRecordsDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelValidator {

    public List<String> validate(IngestionManifest ingestionManifest, SourceRecordsDocument sourceRecords_Document_) {
        List<SourceRecord> sourceRecords = sourceRecords_Document_.sourceRecords();
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (SourceRecord record : sourceRecords) {
            ids.add(record.id());;
            sourceUrls.add(record.sourceUrl());
        }
        if (ids.size() != sourceRecords.size()) { //verify each source has a unique ids
            errors.add("ids.size() != source.sourceRecords().size() : " +
                    ids.size() + " != " + sourceRecords.size());
        }
        if (sourceUrls.size() != sourceRecords.size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != source.sourceRecords().size() : " +
                    sourceUrls.size() +" != " + sourceRecords.size());
        }
        return errors;
    }

}
