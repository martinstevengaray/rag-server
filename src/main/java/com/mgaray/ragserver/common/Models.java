package com.mgaray.ragserver.common;

import java.util.List;

public class Models {

    //-----source dataset-----------------------------------------------------------------------------------------------
    public record SourceCatalog(String title,
                                List<Source> sources) {}

    public record Source(String id,
                         String sourceUrl,
                         String retrievedAt,
                         String title,
                         String location) {}


    //-----ingestion, chunking, embedding, vectorstore------------------------------------------------------------------

    public record IngestionManifest(String id,
                                    RunDefinition runDefinition,
                                    List<SourceRecord> sourceRecords,
                                    VectorStoreSpec vectorStoreSpec) {}

    public record RunDefinition(ChunkingSpec chunkingSpec,
                                EmbeddingSpec embeddingSpec) {}

    public record VectorStoreSpec(String inMemoryVectorStoreExportLocation,
                                  String s3VectorStoreManifestLocation) {}

    public record S3VectorStoreManifest(String s3VectorStoreBucket,
                                        String s3VectorStoreIndexName) {}

    public record SourceRecord(String id,
                               String sourceUrl,
                               String retrievedAt,
                               String title,
                               String textLocation,
                               String chunkManifestLocation) {}

    public record ChunkManifest(List<Chunk> chunks) {}


    public interface IVectorRecord { String id(); }

    public record Chunk(SourceRecord sourceRecord,
                        int index,
                        String textLocation,
                        String embeddingLocation)  implements IVectorRecord {
        public String id() { return sourceRecord.id + ":" + index; }
    }

    public record ChunkingSpec(int wordCount,
                               float percentOverlap) {}

    public record EmbeddingSpec(EmbeddingModelType embeddingModelType) {}

    public enum EmbeddingModelType { DUMMY, BGE_SMALL_EN_V15_QUANTIZED, OPEN_AI_TEXT_EMBEDDING_3_SMALL }


    //-----Webapp-------------------------------------------------------------------------------------------------------

    public record VectorMatch<T extends IVectorRecord> (T record, double matchScore) {}

    //-----Execution Parameters-----------------------------------------------------------------------------------------

    public record BootstrapperConfig(int numberOfEmbeddingThreads, String openApiKey) {}

    public record WebappConfig(ChatModelType chatModelType,
                               int chunksToProvide,
                               String openApiKey,
                               String symmetricSigningKey) {}

    public enum ChatModelType { OPEN_AI_GPT_4O_MINI, OPEN_AI_GPT_4O, OPEN_AI_GPT_56_SOL }









    //used in DataInitializer
    public static String sourceRecordTextLocation(String sourceManifestId, String sourceRecordId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/sourceRecord.txt";
    }

    //used in DataInitializer
    public static String chunkManifestLocation(String sourceManifestId, String sourceRecordId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunkManifest.json";
    }

    //used in Chunker
    public static String chunkTextLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/chunks/" + chunkId + ".txt";
    }

    //used in Chunker
    public static String embeddingLocation(String sourceManifestId, String sourceRecordId, String chunkId) {
        return sourceManifestId + "/sourceRecords/" + sourceRecordId + "/embeddings/" + chunkId + ".bin";
    }

    //Used in many places
    public static String ingestManifestLocation(String sourceManifestId) {
        return sourceManifestId + "/sourceManifest.json";
    }

    public static String inMemoryVectorStoreExportLocation(String sourceManifestId) {
        return sourceManifestId + "/vectorStore.json.gz";
    }

    //used in DataInitializer
    public static String s3VectorStoreManifestLocation(String sourceManifestId) {
        return sourceManifestId + "/s3VectorStore.json";
    }

/*

    Although sourceManifest can support any storage pattern, this is the convention we use

    S3 folder structure:
        S3://bucket/sourceManifestId/sourceManifest.json
        S3://bucket/sourceManifestId/vectorStore.json.gz
        S3://bucket/sourceManifestId/embeddings/local-BgeSmallEnV15Quantized.json
        S3://bucket/sourceManifestId/embeddings/open-ai-text-embedding-3-small.json
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/sourceRecord.txt
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/chunkManifest.txt
        S3://bucket/sourceManifestId/sourceRecords/ssurceRecordId/chunks/chunk.id.txt
        S3://bucket/sourceManifestId/sourceRecords/sourceRecordId/embeddings/chunk.id.txt

    Example:
        S3://bucket/portland-city-code/sourceManifest.json
        S3://bucket/portland-city-code/vectorStore.json.gz
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
