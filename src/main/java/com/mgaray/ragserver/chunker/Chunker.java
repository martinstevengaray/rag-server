package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.Models;

import java.util.ArrayList;
import java.util.List;

public class Chunker {


    public Chunker(String corpusBaseFolder) {
        //expected folder structure:
        //  title-XX.html
        //  title-XX.json
        //  title-XX.txt




    }

    public Models.Chunks chunker(Models.SourceRecords sourceRecords) {
        List<Models.Chunk> chunks = new ArrayList<>();
        for (Models.SourceRecord sourceRecord : sourceRecords.sourceRecords()) {
            chunks.add(chunker(sourceRecord));
        }
        return new Models.Chunks(chunks);
    }

    public Models.Chunk chunker(Models.SourceRecord sourceRecord) {
        return null;
    }

}
