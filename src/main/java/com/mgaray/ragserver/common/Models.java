package com.mgaray.ragserver.common;

import java.util.List;

public class Models {

    public record RunDefinition(ChunkingSpec chunkingSpec,
                                EmbeddingSpec embeddingSpec) {} //todo

    public record SourceManifest(String id,
                                 //RunDefinition //todo
                                 List<SourceRecord> sourceRecords,
                                 List<VectorStoreExport> vectorStoreExports) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               String textLocation,
                               String chunkManifestLocation) {}

    public record ChunkManifest(List<Chunk> chunks,
                                ChunkingSpec chunkingSpec) {} ///todo remove, part of sourceManifest?

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        String textLocation,
                        String embeddingLocation) {}

    public record VectorStoreExport(String modelName,
                                    String location) {}

    public record ChunkingSpec(int wordCount,
                               float percentOverlap) {}

    public record EmbeddingSpec(String modelName) {}

    public record ChunkMatch(Chunk chunk,
                             double matchScore) {}

//todo move the first 2 SourceTransformer
//    public static String originalSourceFolder(String sourceManifestId) {
//        return "/" + sourceManifestId + "/sources";
//    }
    public static String originalSourceManifestLocation(String sourceManifestId) {
        return "/" + sourceManifestId + "/sourceManifest.json";
    }
    public static String originalSourceTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sources/" + sourceRecordId + ".txt";
    }
    public static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }

    public static String chunkManifestLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

    public static String chunkTextLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunks/" + chunkId + ".txt";
    }

    public static String embeddingLocation(String sourceManifestId, String sourceRecordId, String modelName, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/embeddings/" + modelName + "-" + chunkId + ".bin";
    }

    public static String sourceManifestLocation(String sourceManifestId) {
        return "/" + sourceManifestId + "/sourceManifest.json";
    }

    public static String vectorStore(String sourceManifestId, String modelName) {
        return "/" + sourceManifestId + "/embeddings/" + modelName + ".json";
    }
/*

    Although sourceManifest can support any storage pattern, this is the convention we use

    S3 folder structure:
        S3://bucket/sourceManifest.id/sourceManifest.json
        S3://bucket/sourceManifest.id/embeddings/local-BgeSmallEnV15Quantized.json
        S3://bucket/sourceManifest.id/embeddings/open-ai-text-embedding-3-small.json
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
