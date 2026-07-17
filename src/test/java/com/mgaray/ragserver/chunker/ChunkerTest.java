package com.mgaray.ragserver.chunker;

import java.util.List;

public class ChunkerTest {

    public static void main(String[] args) {
        String original ="88\n" +
                ", effective January 3, 2025.)\n" +
                "Whenever a reference is made to this Code as the “Code of the City of Portland, Oregon,” or “Portland City Code” or to any portion of it or to any ordinance of the City, the reference  applies to all of this Code’s amendments, corrections, and additions.\n" +
                "1.01.030 Codification Authority.\n" +
                "(Amended by Ordinance\n" +
                "191988\n" +
                ", effective January 3, 2025.)\n" +
                "This Code consists of all of the regulatory and penal ordinances and certain of the administrative ordinances of the City, codified pursuant to State law.\n" +
                "1.01.035 Auditor to Specify the Form and Style of Code Provisions.\n" +
                "(Added by Ordinance 156865; amended by Ordinance";

        Chunker chunker = new Chunker();
        List<String> chunks = chunker.chunk(original, new Chunker.ChunkingSpec(8, 0.5f));
        for (String chunk : chunks) {
            System.out.println(chunk);
        }

    }

}
