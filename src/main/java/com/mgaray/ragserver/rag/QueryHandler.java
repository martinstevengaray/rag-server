package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.chunker.Embedder;
import com.mgaray.ragserver.chunker.VectorStore;
import com.mgaray.ragserver.common.Models;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

public class QueryHandler {

    public final IDatastore datastore;
    public final VectorStore vectorStore;
    public final EmbeddingModel embeddingModel;

    public QueryHandler(IDatastore datastore, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        String sourceManifestLocation = Models.sourceManifestLocation(sourceManifestId);
        Models.SourceManifest sourceManifest = datastore.fetch(sourceManifestLocation, Models.SourceManifest.class);
        Models.ModelType modelType = sourceManifest.runDefinition().embeddingSpec().modelType();
        this.embeddingModel = Embedder.createModel(modelType);
    }

    public String query(String queryString) {
        float[] searchVector = embeddingModel.embed(queryString).content().vector();
        vectorStore.get(searchVector, 5);
        List<Models.ChunkMatch> chunkMatches = vectorStore.get(searchVector, 5);
        StringBuilder stringBuilder = new StringBuilder();
        for (Models.ChunkMatch chunkMatch : chunkMatches) {
            Models.Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.fetch(chunk.textLocation());
            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
            stringBuilder.append(chunkText);
        }
        return stringBuilder.toString();
    }


    private static String callOpenAi(String prompt) {
        // Configure the client. The API key is read from the OPENAI_API_KEY environment
        // variable (same convention as OpenAiEmbeddingModel in Embedder) so the secret
        // never lives in source. Build this once per call for simplicity; if you make
        // many requests, hoist it into a field since the model is thread-safe and reusable.
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini") // cheap + fast; swap for "gpt-4o" if you need stronger reasoning
                .temperature(0.0)         // deterministic output for RAG-style answers
                .build();

        // Send the prompt and return the model's text response.
        return model.chat(prompt);
    }


    private static String callOpenAiWithOpenAiSdk(String prompt) {
        // Configure the client. fromEnv() reads the OPENAI_API_KEY (and optionally
        // OPENAI_BASE_URL / OPENAI_ORG_ID) environment variables for you. If you'd
        // rather pass the key explicitly, use:
        //   OpenAIOkHttpClient.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
        // The client is thread-safe and reusable — hoist it into a field if you call
        // this often, rather than rebuilding per request.
        OpenAIClient client = OpenAIOkHttpClient.fromEnv();

        // Build the request: model + the messages that make up the conversation.
        // addUserMessage() appends a single "user" role message with your prompt.
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI) // swap for ChatModel.GPT_4O for stronger reasoning
                .temperature(0.0)             // deterministic output for RAG-style answers
                .addUserMessage(prompt)
                .build();

        // Send the request. The response contains a list of choices; we take the first.
        // message().content() is an Optional because tool/function calls can return no text.
        ChatCompletion completion = client.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse("");
    }


    private static String callOpenAiWithRestApi(String prompt) {
        return null;
    }

}
