package com.mgaray.ragserver.sourcereader;

import com.mgaray.ragserver.Models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceValidator {

    public List<String> validate(Models.SourceRecords sourceRecords) {
        List<String> errors = new ArrayList<>();
        Set<String> keys = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (Models.SourceRecord record : sourceRecords.sourceRecords()) {
            keys.add(record.key());;
            sourceUrls.add(record.sourceUrl());
        }
        if (keys.size() != sourceRecords.sourceRecords().size()) { //verify each source has a unique key
            errors.add("keys.size() != source.sourceRecords().size() : " + keys.size() +" != " + sourceRecords.sourceRecords().size());
        }
        if (sourceUrls.size() != sourceRecords.sourceRecords().size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != source.sourceRecords().size() : " + sourceUrls.size() +" != " + sourceRecords.sourceRecords().size());
        }
        return errors;
    }

}
