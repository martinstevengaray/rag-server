package com.mgaray.ragserver;

import java.util.List;

public class ModelRecords {


    public record Chunk(SourceRecord sourceRecord,
                        int chunkIndex,
                        float[] embedding,
                        Resource chunkedTextUrl) {}

    public record Resource(String bucket, String key) {
        public Resource(String key) { this(null, key);}
    }

    public record Source(List<SourceRecord> sourceRecords) {}

    public record SourceRecord(String key,
                               String originalSourceUrl,
                               String retrievedAt,
                               String title,
                               Resource rawTextUrl) {}





    public record SourceRecordPortland(String jurisdiction, String document_type, Integer title_number,
                                String title, String source_url, String retrieved_at) {}

    public record SourceRecordOregon(String jurisdiction, String document_type, Integer title_number,
                              String title, String source_url, String retrieved_at) {}

    public record SourceRecordNab(String jurisdiction, String document_type, Integer title_number,
                           String title, String source_url, String retrieved_at) {}


}
