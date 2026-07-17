package com.mgaray.ragserver;

import java.util.List;

public class ModelRecords {

    public record SourceRecords(List<SourceRecord> sourceRecords) {}

    public record SourceRecord(String key,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               Resource textUrl) {}

    public record Chunks(List<Chunk> chunks) {}

    public record Chunk(SourceRecord sourceRecord,
                        int chunkIndex,
                        float[] embedding,
                        Resource chunkedTextUrl) {}

    public record Resource(String bucket,
                           String key) {}

}
