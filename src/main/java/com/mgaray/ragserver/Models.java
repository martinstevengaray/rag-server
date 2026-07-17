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
                               String chunkManifestLocation) {} //,
//                               //todo the following two may not be necessary
//                               String lazyText,
//                               ChunkManifest lazyChunkManifest) {}

    public record ChunkManifest(List<Chunk> chunks, ChunkingSpec chunkingSpec) {} //}, List<StorageLocation> embeddings) {}

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        String textLocation,
                        String embeddingLocation) {}
//            ,
//                        String lazyText,
//                        float[] lacyEmbedding) {}


    public record ChunkingSpec(int wordCount, float percentOverlap) {}

//    public record StorageLocation(String bucket,
//                                  String key) {}

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
