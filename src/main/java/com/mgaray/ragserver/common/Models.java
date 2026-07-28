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
                                    String sourceRecordsDocumentLocation,
                                    VectorStoreSpec vectorStoreSpec) {}

    public record SourceRecordsDocument(List<SourceRecord> sourceRecords) {}

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

    public enum EmbeddingModelType { DUMMY,
                                     BGE_SMALL_EN_V15_QUANTIZED,
                                     OPEN_AI_TEXT_EMBEDDING_3_SMALL,
                                     OPEN_AI_TEXT_EMBEDDING_3_LARGE }


    //-----Webapp-------------------------------------------------------------------------------------------------------

    public record Request(String userPrompt, String sessionState) {}

    public record Response(String chatResponse, List<String> sources, String sessionState, String details) {}

    public record VectorMatch<T extends IVectorRecord> (T record,
                                                        double matchScore) {}

    //-----Execution Parameters-----------------------------------------------------------------------------------------

    public record BootstrapperConfig(int numberOfEmbeddingThreads,
                                     String openApiKey) {}

    public record WebappConfig(ChatModelType chatModelType,
                               VectorQueryConfig vectorQueryConfig,
                               String openApiKey,
                               String symmetricSigningKey) {}

    public record VectorQueryConfig(int conversationChunkCount,
                                    int mostRecentPromptChunkCount,
                                    int conversationPreviouslyUsedChunkMaxCount) {}

    public enum ChatModelType { OPEN_AI_GPT_4O_MINI,
                                OPEN_AI_GPT_4O,
                                OPEN_AI_GPT_56_SOL }



}

//    public static String ingestManifestLocation(String ingestionManifestId) {
//        return ingestionManifestId + "/ingestionManifest.json";
//    }
////
//    public static IngestionManifest readIngestionManifest(IDatastore datastore, String ingestionManifestId) {
//        String ingestionManifestLocation = ingestManifestLocation(ingestionManifestId);
//        return datastore.readObject(ingestionManifestLocation, IngestionManifest.class);
//    }
//
//    public static void writeIngestionManifest(IDatastore datastore, IngestionManifest ingestionManifest) {
//        String ingestionManifestLocation = ingestManifestLocation(ingestionManifest.id());
//        datastore.writeObject(ingestionManifestLocation, ingestionManifest);
//    }




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

