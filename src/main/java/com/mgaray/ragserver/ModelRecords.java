package com.mgaray.ragserver;

public class ModelRecords {


    public record Chunk(SourceRecord sourceRecord, int chunkIndex, float[] embedding, BucketUrl textSourceUrl) {}

    public record BucketUrl(String bucket, String key) {}

    public record SourceRecord() {} //specific to Portland, Orgeon, or Nab




    public record SourceRecordPortland(String jurisdiction, String document_type, Integer title_number,
                                String title, String source_url, String retrieved_at) {}

    public record SourceRecordOregon(String jurisdiction, String document_type, Integer title_number,
                              String title, String source_url, String retrieved_at) {}

    public record SourceRecordNab(String jurisdiction, String document_type, Integer title_number,
                           String title, String source_url, String retrieved_at) {}


}
