package com.mgaray.ragserver;

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
        public String id() { return sourceRecord.id() + ":" + index; }
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

    public record IngestionConfig(int numberOfEmbeddingThreads,
                                  String openAiApiKey) {}

    public record WebappConfig(ChatModelType chatModelType,
                               VectorQueryConfig vectorQueryConfig,
                               String openAiApiKey,
                               String symmetricSigningKey) {}

    public record VectorQueryConfig(int conversationChunkCount,
                                    int mostRecentPromptChunkCount,
                                    int conversationPreviouslyUsedChunkMaxCount) {}

    public enum ChatModelType { OPEN_AI_GPT_4O_MINI,
                                OPEN_AI_GPT_4O,
                                OPEN_AI_GPT_56_SOL }

}
