package com.mgaray.ragserver.util;

import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {

    @Test
    void roundTripsARecord() {
        SourceCatalog catalog = new SourceCatalog("city codes",
                List.of(new Source("id-1", "https://example.com/1", "2026-01-01", "Title One", "in/1.txt")));

        SourceCatalog parsed = JsonUtils.toObject(JsonUtils.toJson(catalog), SourceCatalog.class);

        assertEquals(catalog, parsed);
    }

    @Test
    void toJsonPrettyProducesSameValueAsToJson() {
        ChunkingSpec spec = new ChunkingSpec(250, 0.25f);

        String compact = JsonUtils.toJson(spec);
        String pretty = JsonUtils.toJsonPretty(spec);

        assertTrue(pretty.contains("\n"), "pretty output should be multi-line: " + pretty);
        assertEquals(JsonUtils.toObject(compact, ChunkingSpec.class), JsonUtils.toObject(pretty, ChunkingSpec.class));
    }

    @Test
    void parseReadsNestedStructure() {
        Map<String, Object> parsed = JsonUtils.parse("{\"a\":{\"b\":\"c\"},\"n\":3}");

        assertEquals(3, parsed.get("n"));
        assertEquals(Map.of("b", "c"), parsed.get("a"));
    }

    @Test
    void parseRejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> JsonUtils.parse("{not json"));
    }

    @Test
    void toObjectRejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> JsonUtils.toObject("{not json", ChunkingSpec.class));
    }

    @Test
    void getNestedFieldWalksThePath() {
        Map<String, Object> objectMap = JsonUtils.parse("{\"embedded\":{\"text\":\"hello\",\"meta\":{\"k\":\"7\"}}}");

        assertEquals("hello", JsonUtils.getNestedField(objectMap, "embedded", "text"));
        assertEquals("7", JsonUtils.getNestedField(objectMap, "embedded", "meta", "k"));
    }

    @Test
    void getNestedFieldReadsTopLevelKeyWithSingleElementPath() {
        Map<String, Object> objectMap = JsonUtils.parse("{\"id\":\"abc\"}");

        assertEquals("abc", JsonUtils.getNestedField(objectMap, "id"));
    }

    @Test
    void getNestedFieldReturnsNullForMissingLeaf() {
        Map<String, Object> objectMap = JsonUtils.parse("{\"embedded\":{\"text\":\"hello\"}}");

        assertNull(JsonUtils.getNestedField(objectMap, "embedded", "absent"));
    }

    @Test
    void getNestedFieldReturnsNullForMissingIntermediate() {
        Map<String, Object> objectMap = JsonUtils.parse("{\"embedded\":{\"text\":\"hello\"}}");

        assertNull(JsonUtils.getNestedField(objectMap, "absent", "text"));
    }

    @Test
    void getNestedFieldReturnsNullWhenIntermediateIsNotAMap() {
        Map<String, Object> objectMap = JsonUtils.parse("{\"embedded\":\"a string, not a map\"}");

        assertNull(JsonUtils.getNestedField(objectMap, "embedded", "text"));
    }

}
