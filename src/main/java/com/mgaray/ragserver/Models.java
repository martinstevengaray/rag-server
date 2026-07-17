package com.mgaray.ragserver;

import java.util.List;

public class Models {

    public record RunDefinition() {} //todo

    public record SourceManifest(String id, List<SourceRecord> sourceRecords) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               String textLocation,
                               String chunkManifestLocation) {
        public SourceRecord withChunkManifestLocation(String chunkManifestLocation) {
            return new SourceRecord(id, sourceUrl, retrievedAt, title, textLocation, chunkManifestLocation);
        }
    }

    public record ChunkManifest(List<Chunk> chunks, ChunkingSpec chunkingSpec) {} //}, List<StorageLocation> embeddings) {}

    public record Chunk(SourceRecord sourceRecord, //todo remove this field in favor of copying over relevant fields only (for example chunkManifestLocation makes no sense here)
                        int index,
                        String textLocation,
                        String embeddingLocation) {}

    public record ChunkingSpec(int wordCount, float percentOverlap) {}

/*

    Although sourceManifest can define any storage pattern, this is the convention we use

    S3 folder structure:
        S3://bucket/sourceManifest.id/sourceManifest.json
        S3://bucket/sourceManifest.id/embeddings/local-BgeSmallEnV15Quantized.bin
        S3://bucket/sourceManifest.id/embeddings/open-ai-text-embedding-3-small.bin
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/sourceRecord.txt
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/chunkManifest.txt
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/chunks/chunk.id.txt
        S3://bucket/sourceManifest.id/sourceRecords/sourceRecord.id/embeddings/embeddingtype-chunk.id.txt

    Example:
        S3://bucket/portland-city-code/sourceManifest.json
        S3://bucket/portland-city-code/embeddings/local-BgeSmallEnV15Quantized.bin
        S3://bucket/portland-city-code/embeddings/open-ai-text-embedding-3-small.bin
        S3://bucket/portland-city-code/sourceRecords/001/sourceRecord.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/002.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/local-BgeSmallEnV15Quantized-000.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/local-BgeSmallEnV15Quantized-001.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/local-BgeSmallEnV15Quantized-002.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/open-ai-text-embedding-3-small-000.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/open-ai-text-embedding-3-small-001.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/open-ai-text-embedding-3-small-002.txt
        S3://bucket/portland-city-code/sourceRecords/002/sourceRecord.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/002.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/local-BgeSmallEnV15Quantized-000-000.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/local-BgeSmallEnV15Quantized-000-001.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/local-BgeSmallEnV15Quantized-000-002.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/open-ai-text-embedding-3-small-000.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/open-ai-text-embedding-3-small-001.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/open-ai-text-embedding-3-small-002.txt



 */

}
