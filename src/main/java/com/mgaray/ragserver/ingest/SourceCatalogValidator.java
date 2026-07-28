package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceCatalogValidator {

    public static List<String> validate(Models.SourceCatalog sourceCatalog) {
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
