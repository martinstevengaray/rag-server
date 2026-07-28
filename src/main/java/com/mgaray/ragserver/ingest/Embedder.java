package com.mgaray.ragserver.ingest;

import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.ChunkManifest;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.IngestionConfig;
import com.mgaray.ragserver.Models.SourceRecordsDocument;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Embedder {

    private final IDatastore datastore;
    private final IngestionConfig ingestionConfig;
    private final ExecutorService executor;

    public Embedder(IDatastore datastore, IngestionConfig ingestionConfig) {
        this.datastore = datastore;
        this.ingestionConfig = ingestionConfig;
        this.executor = Executors.newFixedThreadPool(ingestionConfig.numberOfEmbeddingThreads());
    }

    public void embed(IngestionManifest ingestionManifest, SourceRecordsDocument sourceRecordsDocument) {
        final EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        final EmbeddingModel embeddingModel = createEmbeddingModel(embeddingSpec, ingestionConfig.openApiKey());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (SourceRecord sourceRecord : sourceRecordsDocument.sourceRecords()) {
                futures.add(executor.submit(() -> embed(sourceRecord, embeddingModel)));
            }
            for (Future<?> future : futures) {
                future.get(); //blocks until done
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    private void embed(SourceRecord sourceRecord, EmbeddingModel embeddingModel) {
        String chunkManifestLocation = sourceRecord.chunkManifestLocation();
        ChunkManifest chunkManifest = datastore.readObject(chunkManifestLocation, ChunkManifest.class);
        for (Chunk chunk : chunkManifest.chunks()) {
            String embeddingLocation = chunk.embeddingLocation();
            if (!datastore.exists(embeddingLocation)) {
                String chunkTextLocation = chunk.textLocation();
                String chunkText = datastore.readString(chunkTextLocation);
                float[] chunkEmbedding = embeddingModel.embed(chunkText).content().vector();
                datastore.writeFloatArray(embeddingLocation, chunkEmbedding);
            }
        }
    }

    public static EmbeddingModel createEmbeddingModel(EmbeddingSpec embeddingSpec, String openApiKey) {
        return switch (embeddingSpec.embeddingModelType()) {
            case DUMMY -> {
                final float[] embedding;
                yield new EmbeddingModel() {
                    @Override
                    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                        float[] dummyVector = new float[]{0f, 0.1f, 0.2f, 0.3f};
                        List<Embedding> embeddings = new ArrayList<>();
                        for (TextSegment textSegment : textSegments) {
                            embeddings.add(new Embedding(dummyVector));
                        }
                        return new Response<List<Embedding>>(embeddings);
                    }
                };
            }
            case BGE_SMALL_EN_V15_QUANTIZED -> new BgeSmallEnV15QuantizedEmbeddingModel();
            case OPEN_AI_TEXT_EMBEDDING_3_SMALL -> OpenAiEmbeddingModel.builder()
                    .apiKey(openApiKey)
                    .modelName("text-embedding-3-small")
                    .build();
            case OPEN_AI_TEXT_EMBEDDING_3_LARGE -> OpenAiEmbeddingModel.builder()
                    .apiKey(openApiKey)
                    .modelName("text-embedding-3-large")
                    .build();
        };
    }
}
