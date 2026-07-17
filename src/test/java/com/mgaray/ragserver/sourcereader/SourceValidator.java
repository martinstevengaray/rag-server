package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceValidator {

    public List<String> validate(Models.SourceManifest sourceManifest) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (Models.SourceRecord record : sourceManifest.sourceRecords()) {
            ids.add(record.id());;
            sourceUrls.add(record.sourceUrl());
        }
        if (ids.size() != sourceManifest.sourceRecords().size()) { //verify each source has a unique ids
            errors.add("ids.size() != source.sourceRecords().size() : " + ids.size() +" != " + sourceManifest.sourceRecords().size());
        }
        if (sourceUrls.size() != sourceManifest.sourceRecords().size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != source.sourceRecords().size() : " + sourceUrls.size() +" != " + sourceManifest.sourceRecords().size());
        }
        return errors;
    }

}
