package com.mgaray.ragserver.common;

import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.SourceRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelValidator {

    public List<String> validate(IngestionManifest ingestionManifest) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (SourceRecord record : ingestionManifest.sourceRecords()) {
            ids.add(record.id());;
            sourceUrls.add(record.sourceUrl());
        }
        if (ids.size() != ingestionManifest.sourceRecords().size()) { //verify each source has a unique ids
            errors.add("ids.size() != source.sourceRecords().size() : " +
                    ids.size() + " != " + ingestionManifest.sourceRecords().size());
        }
        if (sourceUrls.size() != ingestionManifest.sourceRecords().size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != source.sourceRecords().size() : " +
                    sourceUrls.size() +" != " + ingestionManifest.sourceRecords().size());
        }
        return errors;
    }

}
