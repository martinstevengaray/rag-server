package com.mgaray.ragserver;

import java.util.List;

public class Models {

    public record SourceManifest(String id, List<SourceRecord> sourceRecords) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               StorageLocation textLocation,
                               StorageLocation chunkManifestLocation,
                               //todo the following two may not be necessary
                               String lazyText,
                               ChunkManifest lazyChunkManifest) {}

    public record ChunkManifest(List<Chunk> chunks) {}

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        float[] embedding,
                        StorageLocation textLocation,
                        String lazyText) {}

    public record StorageLocation(String bucket,
                                  String key) {}

/*

    Although sourceManifest can define any storage pattern, this is the convention we use in SourceReader

    S3 folder structure:
        S3://bucket/sourceManifest.id/sourceManifest.json
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/source.txt
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/chunkManifest.txt
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/chunks/chunk.id.txt

    Example:
        S3://bucket/portland-city-code/sourceManifest.json
        S3://bucket/portland-city-code/sourceRecords/ors001/source.txt
        S3://bucket/portland-city-code/sourceRecords/ors001/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/ors001/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/ors001/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/ors001/chunks/002.txt
        S3://bucket/portland-city-code/sourceRecords/ors002/source.txt
        S3://bucket/portland-city-code/sourceRecords/ors002/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/ors002/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/ors002/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/ors002/chunks/002.txt



 */

}
