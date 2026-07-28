package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.InMemoryDatastore;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.RunDefinition;
import com.mgaray.ragserver.common.Models.ChunkingSpec;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.ChunkManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.SourceRecordsDocument;

import java.util.ArrayList;
import java.util.List;


public class ChunkerTest {

    private static final String chunkText = """
            y of a county or a treatment provider may establish fees that individuals participating in a treatment court program may be required to pay for treatment and other services provided as part of the treatment court program.
            
                  (b) A court may order an individual participating in a treatment court program to pay fees to participate in the program. Fees imposed under this subsection may not be paid to the court.
            
                  (3) Records that are maintained by the circuit court specifically for the purpose of a treatment court program must be maintained separately from other court records. The Chief Justice of the Supreme Court shall designate a case management system to be used for maintaining treatment court program records, and the records for each treatment court program must be maintained in the designated system for any treatment court program provided with access to the system. The treatment court program shall use the case management system for case management, monitoring and evaluation as required by the standards developed pursuant to ORS 137.680. Records maintained by a circuit court specifically for the purpose of a treatment court program are confidential and may not be disclosed except in accordance with regulations adopted under 42 U.S.C. 290dd-2, including under the circumstances described in subsections (4) to (7) of this section.
            
                  (4) If the individual who is the subject of the record gives written consent, a record described in subsection (3) of this section m
          """;

    public static void main(String[] args) {
        IDatastore dataStore = new InMemoryDatastore();
        List<SourceRecord> sourceRecords = new ArrayList<>();
        String sourceManifestId = "ChunkerTest";
        String sourceRecordId = "ChunkerTest-sourceRecord";
        String sourceLocationTextLocation = "test_sourceLocationTextLocation";;
        dataStore.writeString(sourceLocationTextLocation, chunkText);
        String chunkManifestLocation = "test_chunkManifestLocation";
        SourceRecord sourceRecord = new SourceRecord(
                sourceRecordId,
                null,
                null,
                null,
                sourceLocationTextLocation,
                chunkManifestLocation);
        sourceRecords.add(sourceRecord);
        SourceRecordsDocument sourceRecordsDocument = new SourceRecordsDocument(sourceRecords);
        RunDefinition runDefinition = new RunDefinition(
                new ChunkingSpec(8, 0.5f), null);
        IngestionManifest ingestionManifest =
                new IngestionManifest(sourceManifestId, runDefinition, null, null);

        Chunker chunker = new Chunker(dataStore);
        chunker.chunk(ingestionManifest, sourceRecordsDocument);

        ChunkManifest chunkManifest = dataStore.readObject(chunkManifestLocation, ChunkManifest.class);
        for (Chunk chunk : chunkManifest.chunks()) {
            String text = dataStore.readString(chunk.textLocation());
            System.out.println(text);
        }
    }

}
