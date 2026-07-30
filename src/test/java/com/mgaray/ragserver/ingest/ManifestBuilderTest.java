package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.Models.ChunkingSpec;
import com.mgaray.ragserver.Models.EmbeddingModelType;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.RunDefinition;
import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestBuilderTest {

    private static final String RUN_ID = "run-1";
    private static final RunDefinition RUN_DEFINITION = new RunDefinition(
            new ChunkingSpec(250, 0.25f), new EmbeddingSpec(EmbeddingModelType.DUMMY));

    private final IDatastore sourceDatastore = new InMemoryDatastore();
    private final IDatastore ingestionDatastore = new InMemoryDatastore();
    private final ManifestBuilder manifestBuilder = new ManifestBuilder(sourceDatastore, ingestionDatastore);

    private SourceCatalog catalogWith(String... ids) {
        List<Source> sources = new java.util.ArrayList<>();
        for (String id : ids) {
            sourceDatastore.writeString("in/" + id + ".txt", "text for " + id);
            sources.add(new Source(id, "https://example.com/" + id, "2026-01-01", "Title " + id, "in/" + id + ".txt"));
        }
        return new SourceCatalog("city codes", sources);
    }

    private IngestionManifest create(SourceCatalog sourceCatalog) {
        return manifestBuilder.create(sourceCatalog, RUN_ID, RUN_DEFINITION);
    }

    @Test
    void returnsAManifestCarryingTheIdAndRunDefinition() {
        IngestionManifest ingestionManifest = create(catalogWith("a"));

        assertEquals(RUN_ID, ingestionManifest.id());
        assertEquals(RUN_DEFINITION, ingestionManifest.runDefinition());
    }

    @Test
    void namesLocationsUnderTheRunId() {
        IngestionManifest ingestionManifest = create(catalogWith("a"));

        assertEquals("run-1/sourceRecordsDocument.json", ingestionManifest.sourceRecordsDocumentLocation());
        assertEquals("run-1/vectorStore.json.gz",
                ingestionManifest.vectorStoreSpec().inMemoryVectorStoreExportLocation());
        assertEquals("run-1/s3VectorStore.json",
                ingestionManifest.vectorStoreSpec().s3VectorStoreManifestLocation());
    }

    @Test
    void persistsTheManifestSoALaterStageCanReadItBack() {
        IngestionManifest ingestionManifest = create(catalogWith("a"));

        assertEquals(ingestionManifest, ingestionDatastore.readIngestionManifest(RUN_ID));
    }

    @Test
    void copiesSourceTextIntoTheIngestionDatastore() {
        create(catalogWith("a"));

        assertEquals("text for a", ingestionDatastore.readString("run-1/sourceRecords/a/sourceRecord.txt"));
    }

    @Test
    void leavesTheSourceDatastoreUntouched() {
        create(catalogWith("a"));

        assertEquals("text for a", sourceDatastore.readString("in/a.txt"));
        assertFalse(sourceDatastore.exists("run-1/sourceRecords/a/sourceRecord.txt"),
                "the ingestion run must not write back into the source datastore");
    }

    @Test
    void buildsOneSourceRecordPerCatalogSourceCarryingItsMetadata() {
        IngestionManifest ingestionManifest = create(catalogWith("a", "b"));

        SourceRecordsDocument document = ingestionDatastore.readObject(
                ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);

        assertEquals(2, document.sourceRecords().size());
        SourceRecord first = document.sourceRecords().get(0);
        assertEquals("a", first.id());
        assertEquals("https://example.com/a", first.sourceUrl());
        assertEquals("2026-01-01", first.retrievedAt());
        assertEquals("Title a", first.title());
        assertEquals("run-1/sourceRecords/a/sourceRecord.txt", first.textLocation());
        assertEquals("run-1/sourceRecords/a/chunkManifest.json", first.chunkManifestLocation());
    }

    @Test
    void preservesCatalogOrder() {
        IngestionManifest ingestionManifest = create(catalogWith("c", "a", "b"));

        SourceRecordsDocument document = ingestionDatastore.readObject(
                ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);

        assertEquals(List.of("c", "a", "b"), document.sourceRecords().stream().map(SourceRecord::id).toList());
    }

    @Test
    void handlesAnEmptyCatalog() {
        IngestionManifest ingestionManifest = create(new SourceCatalog("empty", List.of()));

        SourceRecordsDocument document = ingestionDatastore.readObject(
                ingestionManifest.sourceRecordsDocumentLocation(), SourceRecordsDocument.class);

        assertEquals(List.of(), document.sourceRecords());
        assertTrue(ingestionDatastore.exists("run-1/ingestionManifest.json"));
    }

    @Test
    void doesNotRecopySourceTextThatIsAlreadyPresent() {
        SourceCatalog sourceCatalog = catalogWith("a");
        ingestionDatastore.writeString("run-1/sourceRecords/a/sourceRecord.txt", "text from an earlier run");

        create(sourceCatalog);

        assertEquals("text from an earlier run",
                ingestionDatastore.readString("run-1/sourceRecords/a/sourceRecord.txt"));
    }

    @Test
    void doesNotRewriteAnExistingSourceRecordsDocument() {
        SourceCatalog sourceCatalog = catalogWith("a");
        ingestionDatastore.writeObject("run-1/sourceRecordsDocument.json", new SourceRecordsDocument(List.of()));

        create(sourceCatalog);

        SourceRecordsDocument document = ingestionDatastore.readObject(
                "run-1/sourceRecordsDocument.json", SourceRecordsDocument.class);
        assertEquals(List.of(), document.sourceRecords(), "an existing document is left alone");
    }

    @Test
    void rerunningWithTheSameInputsIsIdempotent() {
        SourceCatalog sourceCatalog = catalogWith("a", "b");

        IngestionManifest first = create(sourceCatalog);
        IngestionManifest second = create(sourceCatalog);

        assertEquals(first, second);
    }

    @Test
    void differentRunIdsGetSeparateLocations() {
        SourceCatalog sourceCatalog = catalogWith("a");

        manifestBuilder.create(sourceCatalog, "run-1", RUN_DEFINITION);
        manifestBuilder.create(sourceCatalog, "run-2", RUN_DEFINITION);

        assertTrue(ingestionDatastore.exists("run-1/sourceRecords/a/sourceRecord.txt"));
        assertTrue(ingestionDatastore.exists("run-2/sourceRecords/a/sourceRecord.txt"));
        assertEquals("run-2", ingestionDatastore.readIngestionManifest("run-2").id());
    }

}
