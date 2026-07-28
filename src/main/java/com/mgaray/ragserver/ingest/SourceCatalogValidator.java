package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.Source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceCatalogValidator {

    public static List<String> validate(SourceCatalog sourceCatalog) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();;
        Set<String> sourceUrls = new HashSet<>();
        for (Source source : sourceCatalog.sources()) {
            ids.add(source.id());
            sourceUrls.add(source.sourceUrl());
        }
        if (ids.size() != sourceCatalog.sources().size()) { //verify each source has a unique ids
            errors.add("ids.size() != sourceCatalog.sources().size() : " + ids.size() +" != " + sourceCatalog.sources().size());
        }
        if (sourceUrls.size() != sourceCatalog.sources().size()) { //verify each source has a unique sourceUrl
            errors.add("sourceUrls.size() != sourceCatalog.sources().size(): " + sourceUrls.size() +" != " + sourceCatalog.sources().size());
        }
        return errors;
    }

}
