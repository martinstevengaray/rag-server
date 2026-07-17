package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;
import com.mgaray.ragserver.common.FileUtils;

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
        List<Models.SourceRecord> sourceRecords = new ArrayList<>();
        Models.StorageLocation sourceLocation = new Models.StorageLocation(null, "./test/java/com/mgaray/ragserver/chunker/source.txt");
        Models.SourceRecord sourceRecord = new Models.SourceRecord(
                "ChunkerTest.sourceRecord",
                null,
                null,
                null,
                sourceLocation,
                null,
                chunkText,
                null);
        sourceRecords.add(sourceRecord);
        Models.SourceManifest sourceManifest = new Models.SourceManifest("ChunkerTest", sourceRecords);

        Chunker chunker = new Chunker();
        Models.ChunkManifest chunkManifest = chunker.chunk(sourceManifest, new Chunker.ChunkingSpec(8, 0.5f));
        for (Models.Chunk chunk : chunkManifest.chunks()) {
            System.out.println(chunk.lazyText());
        }

    }

}
