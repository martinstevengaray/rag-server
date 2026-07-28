package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceCatalogValidatorTest {

    private static Source source(String id, String sourceUrl) {
        return new Source(id, sourceUrl, "2026-01-01", "Title " + id, "in/" + id + ".txt");
    }

    private static SourceCatalog catalog(Source... sources) {
        return new SourceCatalog("city codes", List.of(sources));
    }

    @Test
    void acceptsACatalogWithUniqueIdsAndUrls() {
        List<String> errors = SourceCatalogValidator.validate(catalog(
                source("a", "https://example.com/a"),
                source("b", "https://example.com/b")));

        assertEquals(List.of(), errors);
    }

    @Test
    void acceptsAnEmptyCatalog() {
        assertEquals(List.of(), SourceCatalogValidator.validate(catalog()));
    }

    @Test
    void acceptsASingleSource() {
        assertEquals(List.of(), SourceCatalogValidator.validate(catalog(source("a", "https://example.com/a"))));
    }

    @Test
    void rejectsDuplicateIds() {
        List<String> errors = SourceCatalogValidator.validate(catalog(
                source("a", "https://example.com/a"),
                source("a", "https://example.com/b")));

        assertEquals(1, errors.size(), "expected exactly the duplicate-id error, got " + errors);
        assertTrue(errors.get(0).startsWith("ids.size()"), errors.get(0));
        assertTrue(errors.get(0).contains("1 != 2"), errors.get(0));
    }

    @Test
    void rejectsDuplicateSourceUrls() {
        List<String> errors = SourceCatalogValidator.validate(catalog(
                source("a", "https://example.com/same"),
                source("b", "https://example.com/same")));

        assertEquals(1, errors.size(), "expected exactly the duplicate-url error, got " + errors);
        assertTrue(errors.get(0).startsWith("sourceUrls.size()"), errors.get(0));
        assertTrue(errors.get(0).contains("1 != 2"), errors.get(0));
    }

    @Test
    void reportsBothProblemsWhenIdAndUrlAreDuplicated() {
        List<String> errors = SourceCatalogValidator.validate(catalog(
                source("a", "https://example.com/same"),
                source("a", "https://example.com/same")));

        assertEquals(2, errors.size(), "expected both errors, got " + errors);
    }

    @Test
    void countsDuplicatesAcrossManySources() {
        List<String> errors = SourceCatalogValidator.validate(catalog(
                source("a", "https://example.com/a"),
                source("b", "https://example.com/b"),
                source("a", "https://example.com/c"),
                source("a", "https://example.com/d")));

        assertEquals(1, errors.size(), "expected exactly the duplicate-id error, got " + errors);
        assertTrue(errors.get(0).contains("2 != 4"), errors.get(0));
    }

}
