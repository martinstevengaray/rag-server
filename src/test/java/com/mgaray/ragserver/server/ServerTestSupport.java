package com.mgaray.ragserver.server;

import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.SourceRecord;
import com.mgaray.ragserver.Models.VectorMatch;
import com.mgaray.ragserver.Models.VectorStoreSpec;
import com.mgaray.ragserver.crypto.EncryptionDelegate;
import com.mgaray.ragserver.logger.ILogger;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.util.JsonUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test doubles shared by {@link QueryHandlerTest} and {@link LambdaServerTest}. Everything the
 * server package reaches for at runtime — OpenAI chat, OpenAI embeddings, the vector store — is
 * replaced with a deterministic stand-in so the tests never touch the network.
 */
final class ServerTestSupport {

    private ServerTestSupport() {}

    /** A valid 256-bit AES key. Session state is encrypted, so tests must use this key to read it. */
    static final String SIGNING_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static final EncryptionDelegate ENCRYPTION_DELEGATE = new EncryptionDelegate(SIGNING_KEY);

    /** Wraps plain session-state json the way an earlier response would have handed it to the browser. */
    static String encryptedSessionState(String sessionStateJson) {
        return ENCRYPTION_DELEGATE.encrypt(sessionStateJson);
    }

    /** Unwraps the session state on a response so tests can assert on what was recorded in it. */
    static String decryptedSessionState(String sessionState) {
        return ENCRYPTION_DELEGATE.decrypt(sessionState);
    }

    static SourceRecord sourceRecord(String id) {
        return new SourceRecord(id, "https://example.com/" + id, "2026-01-01", "Title " + id,
                "run-1/sourceRecords/" + id + "/sourceRecord.txt",
                "run-1/sourceRecords/" + id + "/chunkManifest.json");
    }

    static Chunk chunk(String sourceRecordId, int index) {
        return new Chunk(sourceRecord(sourceRecordId), index,
                "run-1/sourceRecords/" + sourceRecordId + "/chunks/" + index + ".txt",
                "run-1/sourceRecords/" + sourceRecordId + "/embeddings/" + index + ".bin");
    }

    /** Returns whatever json it was primed with, and records every prompt it was handed. */
    static final class FakeChatModel implements ChatModel {
        private final Function<String, String> responder;
        final List<String> prompts = new ArrayList<>();

        FakeChatModel(String response) {
            this.responder = prompt -> response;
        }

        /**
         * Builds the reply from the prompt. Needed whenever a test asserts on citations: the
         * data-source ids are random UUIDs minted inside {@code query}, so the only way to cite a
         * real one is to read it back out of the prompt.
         */
        FakeChatModel(Function<String, String> responder) {
            this.responder = responder;
        }

        @Override
        public String chat(String prompt) {
            prompts.add(prompt);
            return responder.apply(prompt);
        }

        String lastPrompt() {
            return prompts.get(prompts.size() - 1);
        }
    }

    /** Pulls the generated data-source ids, in order, out of a prompt. */
    static List<String> dataSourceIdsIn(String prompt) {
        return prompt.lines()
                .filter(line -> line.startsWith("{\"id\":"))
                .map(line -> JsonUtils.parse(line).get("id").toString())
                .toList();
    }

    /** Builds the json shape QueryHandler expects back from the chat model. */
    static String chatResponse(String response, List<String> dataSourcesUsed) {
        return JsonUtils.toJson(Map.of("dataSourcesUsed", dataSourcesUsed, "response", response));
    }

    /**
     * Hands out a distinct one-dimensional vector per call so tests can tell the two searches
     * QueryHandler performs (conversation-wide, then most-recent-prompt) apart.
     */
    static final class FakeEmbeddingModel implements EmbeddingModel {
        final List<String> embedded = new ArrayList<>();

        @Override
        public Response<Embedding> embed(String text) {
            embedded.add(text);
            return Response.from(Embedding.from(new float[]{embedded.size()}));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = new ArrayList<>();
            for (TextSegment textSegment : textSegments) {
                embeddings.add(embed(textSegment).content());
            }
            return Response.from(embeddings);
        }
    }

    /**
     * Returns a scripted result list per {@code get(vector, topK)} call, in call order, and keeps
     * an id lookup so {@code get(id)} behaves like a loaded store.
     */
    static final class FakeVectorStore implements IVectorStore<Chunk> {
        private final List<List<Chunk>> resultsPerCall = new ArrayList<>();
        private final Map<String, Chunk> chunksById = new LinkedHashMap<>();
        final List<Integer> topKsRequested = new ArrayList<>();
        final List<String> idsLookedUp = new ArrayList<>();
        private int callCount = 0;

        @SafeVarargs
        FakeVectorStore(List<Chunk>... resultsPerCall) {
            this.resultsPerCall.addAll(List.of(resultsPerCall));
            for (List<Chunk> results : resultsPerCall) {
                for (Chunk chunk : results) {
                    chunksById.put(chunk.id(), chunk);
                }
            }
        }

        /** Makes a chunk resolvable by id without it being returned from a similarity search. */
        FakeVectorStore knowing(Chunk... chunks) {
            for (Chunk chunk : chunks) {
                chunksById.put(chunk.id(), chunk);
            }
            return this;
        }

        @Override
        public void add(float[] vector, Chunk chunk) {
            chunksById.put(chunk.id(), chunk);
        }

        @Override
        public List<VectorMatch<Chunk>> get(float[] searchVector, int topK) {
            topKsRequested.add(topK);
            List<Chunk> results = callCount < resultsPerCall.size() ? resultsPerCall.get(callCount) : List.of();
            callCount++;
            List<VectorMatch<Chunk>> matches = new ArrayList<>();
            for (Chunk chunk : results) {
                matches.add(new VectorMatch<>(chunk, 1.0));
            }
            return matches;
        }

        @Override
        public Chunk get(String id) {
            idsLookedUp.add(id);
            return chunksById.get(id);
        }

        @Override
        public void initialize(EmbeddingSpec embeddingSpec) {}

        @Override
        public boolean resultsExist(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {
            return true;
        }

        @Override
        public void writeResults(IDatastore datastore, VectorStoreSpec vectorStoreSpec) {}

        @Override
        public boolean exists(Chunk chunk) {
            return chunksById.containsKey(chunk.id());
        }
    }

    /** Captures log output so tests can assert on the error paths. */
    static final class RecordingLogger implements ILogger {
        final List<String> messages = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        @Override
        public void log(String message, Map<String, Object> objectMap) {
            messages.add(message);
        }

        @Override
        public void log(String message, Object object) {
            messages.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public void error(String message, Exception e) {
            errors.add(message);
        }
    }

}
