package com.mgaray.ragserver.chunker;

public class Chunker {


    public Chunker(String corpusBaseFolder) {
        //expected folder structure:
        //  title-XX.html
        //  title-XX.json
        //  title-XX.txt




    }

    record ChunckRecord(SourceRecord sourceRecord, int chunkIndex, float[] embedding, String textSourceS3Url) {}

    record SourceRecord() {}

    record SourceRecordPortland(String jurisdiction, String document_type, Integer title_number,
                  String title, String source_url, String retrieved_at) {}

    record SourceRecordOregon(String jurisdiction, String document_type, Integer title_number,
                           String title, String source_url, String retrieved_at) {}

    record SourceRecordNab(String jurisdiction, String document_type, Integer title_number,
                                String title, String source_url, String retrieved_at) {}

}
