package com.mgaray.ragserver.common;

import java.util.List;

public class Models {

    public record RunDefinition(ChunkingSpec chunkingSpec,
                                EmbeddingSpec embeddingSpec) {}

    public record SourceManifest(String id,
                                 RunDefinition runDefinition,
                                 List<SourceRecord> sourceRecords,
                                 List<VectorStoreExport> vectorStoreExports) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               String textLocation,
                               String chunkManifestLocation) {}

    public record ChunkManifest(List<Chunk> chunks) {}

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        String textLocation,
                        String embeddingLocation) {}

    public record VectorStoreExport(String modelName,
                                    String location) {}

    public record ChunkingSpec(int wordCount,
                               float percentOverlap) {}

    public record EmbeddingSpec(ModelType modelType) {}

    public record ChunkMatch(Chunk chunk,
                             double matchScore) {}

    public enum ModelType { DUMMY, BGE_SMALL_EN_V15_QUANTIZED, OPEN_AI_TEXT_EMBEDDING_3_SMALL }


    public static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }

    public static String chunkManifestLocation(String sourceManifestId, String sourceRecordId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

    public static String chunkTextLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunks/" + chunkId + ".txt";
    }

    public static String embeddingLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return "/" + sourceManifestId + "/sourceRecords/" + sourceRecordId + "/embeddings/" + chunkId + ".bin";
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
        S3://bucket/sourceManifestId/sourceManifest.json
        S3://bucket/sourceManifestId/vectorStore.json.zip
        S3://bucket/sourceManifestId/embeddings/local-BgeSmallEnV15Quantized.json
        S3://bucket/sourceManifestId/embeddings/open-ai-text-embedding-3-small.json
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/sourceRecord.txt
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/chunkManifest.txt
        S3://bucket/sourceManifestId/sourceRecords/ssurceRecordId/chunks/chunk.id.txt
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/embeddings/chunk.id.txt

    Example:
        S3://bucket/portland-city-code/sourceManifest.json
        S3://bucket/portland-city-code/vectorStore.json.zip
        S3://bucket/portland-city-code/sourceRecords/001/sourceRecord.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/001/chunks/002.txt
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/000.bin
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/001.bin
        S3://bucket/portland-city-code/sourceRecords/001/embeddings/002.bin
        S3://bucket/portland-city-code/sourceRecords/002/sourceRecord.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunkManifest.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/000.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/001.txt
        S3://bucket/portland-city-code/sourceRecords/002/chunks/002.txt
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/000.bin
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/001.bin
        S3://bucket/portland-city-code/sourceRecords/002/embeddings/002.bin



 */

}
