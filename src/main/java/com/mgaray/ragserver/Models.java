package com.mgaray.ragserver;

import java.util.List;

public class Models {

    public record SourceManifest(List<SourceRecord> sourceRecords) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               StorageLocation textLocation) {}

    public record ChunkManifest(List<Chunk> chunks) {}

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        float[] embedding,
                        StorageLocation textLocation) {}

    public record StorageLocation(String bucket,
                                  String key) {}

}
