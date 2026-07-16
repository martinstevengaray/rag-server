package com.mgaray.ragserver.chunker;

import com.mgaray.ragserver.ModelRecords;
import com.mgaray.ragserver.awsresources.BucketResource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.Arrays;

public class Embedder {


    private final BucketResource bucketResource = new BucketResource();


    public void mutateEmbedding(ModelRecords.Chunk chunk) {
//        String chunkOfText = bucketResource.fetch(chunk.textSourceUrl());
//
//        Embedding embedding = model.embed(chunkOfText).content();
//
//        float[] vector = embedding.vector();

    }

    public static void main(String[] args) {
        Embedder embedder = new Embedder();
        embedder.exampleOne();
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

        String search1 = "I'm looking for some example greetings";
        String search2 = "I'm not feeling well";
        String search3 = "I love animals";

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
}
