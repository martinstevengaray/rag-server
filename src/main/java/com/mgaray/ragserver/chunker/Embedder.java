package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.SourceRecord;
import com.mgaray.ragserver.common.Models.ChunkManifest;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.ModelType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Embedder {

    private final ExecutorService executor = Executors.newFixedThreadPool(10); //todo harcoded
    private final IDatastore dataStore;

    public Embedder(IDatastore dataStore) {
        this.dataStore = dataStore;
    }

    public void embed(IngestionManifest ingestionManifest) {
        final EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        final EmbeddingModel embeddingModel = createEmbeddingModel(embeddingSpec.modelType());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (SourceRecord sourceRecord : ingestionManifest.sourceRecords()) {
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
        ChunkManifest chunkManifest = dataStore.readObject(chunkManifestLocation, ChunkManifest.class);
        for (Chunk chunk : chunkManifest.chunks()) {
            String chunkTextLocation = chunk.textLocation();
            String embeddingLocation = chunk.embeddingLocation();
            String chunkText = dataStore.readString(chunkTextLocation);
            if (!dataStore.exists(embeddingLocation)) {
                float[] chunkEmbedding = embeddingModel.embed(chunkText).content().vector();
                dataStore.writeEmbedding(embeddingLocation, chunkEmbedding);
            }
        }
    }

    public static EmbeddingModel createEmbeddingModel(ModelType modelType) {
        return switch (modelType) {
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
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName("text-embedding-3-small") // Standard, high-performance model //consider: text-embedding-3-large
                    .build();
            default -> throw new IllegalArgumentException("Unsupported embedding ModelType: " + modelType);
        };
    }
}







/*



//    private final DataFetcher dataFetcher = new DataFetcher();


    public void mutateEmbedding(Models.Chunk chunk) {
//        String chunkOfText = bucketResource.fetch(chunk.textSourceUrl());
//
//        Embedding embedding = model.embed(chunkOfText).content();
//
//        float[] vector = embedding.vector();

    }


    public static void main(String[] args) {
        Embedder embedder = new Embedder(ModelType.LOCAL);
        embedder.exampleThree();
    }
    public void exampleThree() {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        String[] texts = {"hi there how are you", "welcome to my home", "was there a dog in the mall?"};
        for (String text : texts) {
            TextSegment segment = TextSegment.from(text);
            Embedding embedding = embeddingModel.embed(segment).content();
            store.add(embedding, segment);
        }

        String search = "I love animals";//"I'm looking for some example greetings";

        // BGE retrieves best when the query (not the documents) carries this prefix
        Embedding searchEmbedding = embeddingModel
                .embed("Represent this sentence for searching relevant passages: " + search)
                .content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(searchEmbedding)
                .maxResults(3)
                .build();

        // matches come back best-first; score is relevance [0..1] = (cosine + 1) / 2
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf("score %.4f (cosine %.4f): \"%s\"%n",
                    match.score(),
                    CosineSimilarity.fromRelevanceScore(match.score()),
                    match.embedded().text());
        }

        System.out.println("Closest match for \"" + search + "\" is \"" + matches.get(0).embedded().text() + "\"");
    }

    public void exampleOne() {

// Runs fully offline with zero configuration
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();


        Embedding embedding1 = embeddingModel.embed("hi there how are you").content();
        Embedding embedding2 = embeddingModel.embed("welcome to my home").content();
        Embedding embedding3 = embeddingModel.embed("was there a dog in the mall?").content();

        // 4. Extract the raw numerical vector (float array)
        float[] vector1 = embedding1.vector();
        float[] vector2 = embedding2.vector();
        float[] vector3 = embedding3.vector();

        System.out.println(Arrays.toString(vector1));
        System.out.println(Arrays.toString(vector2));
        System.out.println(Arrays.toString(vector3));

        String search1 = "I love dogs";//"I'm looking for some example greetings";
//        String search2 = "I'm not feeling well";
//        String search3 = "I love animals";

        // BGE retrieves best when the query (not the documents) carries this prefix
        Embedding searchEmbedding = embeddingModel
                .embed("Represent this sentence for searching relevant passages: " + search1)
//                .embed(search1)
                .content();

        String[] texts = {"hi there how are you", "welcome to my home", "was there a dog in the mall?"};
        Embedding[] documents = {embedding1, embedding2, embedding3};

        int closest = 0;
        double bestScore = -1;
        for (int i = 0; i < documents.length; i++) {
            double score = CosineSimilarity.between(searchEmbedding, documents[i]);
            System.out.printf("similarity to \"%s\": %.4f%n", texts[i], score);
            if (score > bestScore) {
                bestScore = score;
                closest = i;
            }
        }

        System.out.println("Closest match for \"" + search1 + "\" is \"" + texts[closest] + "\"");
    }

    public void exampleTwo() {
        // 1. Initialize the embedding model (requires your OPENAI_API_KEY environment variable)
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-small") // Standard, high-performance model
                //text-embedding-3-large
                .build();

        // 2. Define the text chunk you want to vectorize
        String textChunk = "Java is a multi-platform, object-oriented programming language that runs on billions of devices.";

        System.out.println("Generating embedding for: \"" + textChunk + "\"...\n");

        // 3. Request the embedding vector from the provider
        Embedding embedding = embeddingModel.embed(textChunk).content();

        // 4. Extract the raw numerical vector (float array)
        float[] vector = embedding.vector();

        // 5. Inspect the generated high-dimensional data
        System.out.println("Vector generated successfully!");
        System.out.println("Vector Dimension Count: " + vector.length); // Typically 1536 dimensions for OpenAI
        System.out.println("First 5 values of the vector: " + Arrays.toString(Arrays.copyOfRange(vector, 0, 5)));
    }

    static double dotProduct(Embedding a, Embedding b) {
        float[] x = a.vector();
        float[] y = b.vector();
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i] * y[i];
        }
        return sum;
    }
}
*/