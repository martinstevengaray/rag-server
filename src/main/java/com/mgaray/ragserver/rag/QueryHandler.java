package com.mgaray.ragserver.rag;

import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.bootstrap.Embedder;
import com.mgaray.ragserver.bootstrap.VectorStore;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.ModelType;
import com.mgaray.ragserver.common.Models.ChunkMatch;
import com.mgaray.ragserver.common.Models.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

import static com.mgaray.ragserver.common.Models.ingestManifestLocation;

public class QueryHandler {

    public final IDatastore datastore;
    public final VectorStore vectorStore;
    public final EmbeddingModel embeddingModel;
    public final ChatModel chatModel;

    public QueryHandler(IDatastore datastore, String openAiApiKey, String sourceManifestId) {
        this.datastore = datastore;
        this.vectorStore = VectorStore.load(datastore, sourceManifestId);
        String sourceManifestLocation = ingestManifestLocation(sourceManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(sourceManifestLocation, IngestionManifest.class);
        ModelType modelType = ingestionManifest.runDefinition().embeddingSpec().modelType();
        this.embeddingModel = Embedder.createEmbeddingModel(modelType);
        this.chatModel = createChatModel(openAiApiKey);
    }

    public String query(String queryString) {
        float[] searchVector = embeddingModel.embed(queryString).content().vector();
        vectorStore.get(searchVector, 5);
        List<ChunkMatch> chunkMatches = vectorStore.get(searchVector, 50); //todo count hardcode
        StringBuilder stringBuilder = new StringBuilder();
        for (ChunkMatch chunkMatch : chunkMatches) {
            Chunk chunk = chunkMatch.chunk();
            String chunkText = datastore.readString(chunk.textLocation());
            stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
            stringBuilder.append(chunkText);
        }
        stringBuilder.append("\n\n--------------------------------------------------------------\n\n");
        String prompt = "only use the the following chunks of data to answer the question presented at the end." +
                "If unable to answer the question based on the chunk sources say so.\n\n" +
                "chunks: " + stringBuilder.toString() + "\n\n" +
                "question to answer: " + queryString;
        return chatModel.chat(prompt) + "\n\n\n\n" + prompt;
    }

    public static ChatModel createChatModel(String openAiApiKey) {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-5.6-sol") //todo hardcoded //"gpt-4o-mini") // also consider "gpt-4o"     "gpt-5.6") //"gpt-5.6-sol")  //"
                //.temperature(0.0)         // 0.0 = deterministic output , "gpt-5.6-sol" does not support temperature!=1
                .build();
    }

}
