package com.mgaray.ragserver.server;

import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.Request;
import com.mgaray.ragserver.Models.Response;
import com.mgaray.ragserver.Models.VectorQueryConfig;
import com.mgaray.ragserver.Models.WebappConfig;
import com.mgaray.ragserver.crypto.EncryptionDelegate;
import com.mgaray.ragserver.server.ServerTestSupport.FakeChatModel;
import com.mgaray.ragserver.server.ServerTestSupport.FakeEmbeddingModel;
import com.mgaray.ragserver.server.ServerTestSupport.FakeVectorStore;
import com.mgaray.ragserver.server.ServerTestSupport.RecordingLogger;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.mgaray.ragserver.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static java.nio.charset.StandardCharsets.UTF_8;
import static com.mgaray.ragserver.server.ServerTestSupport.SIGNING_KEY;
import static com.mgaray.ragserver.server.ServerTestSupport.chunk;
import static com.mgaray.ragserver.server.ServerTestSupport.dataSourceIdsIn;
import static com.mgaray.ragserver.server.ServerTestSupport.decryptedSessionState;
import static com.mgaray.ragserver.server.ServerTestSupport.encryptedSessionState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryHandlerTest {

    private static final Chunk CHUNK_A = chunk("a", 0);
    private static final Chunk CHUNK_B = chunk("b", 0);

    private final IDatastore datastore = new InMemoryDatastore();
    private final RecordingLogger logger = new RecordingLogger();

    private QueryHandler queryHandler(FakeChatModel chatModel,
                                      FakeEmbeddingModel embeddingModel,
                                      FakeVectorStore vectorStore,
                                      VectorQueryConfig vectorQueryConfig) {
        WebappConfig webappConfig =
                new WebappConfig(OPEN_AI_GPT_4O_MINI, vectorQueryConfig, "unused-key", SIGNING_KEY);
        return new QueryHandler(webappConfig, datastore, vectorStore, embeddingModel, chatModel,
                new EncryptionDelegate(SIGNING_KEY));
    }

    private static VectorQueryConfig config(int conversation, int mostRecent, int previouslyUsedMax) {
        return new VectorQueryConfig(conversation, mostRecent, previouslyUsedMax);
    }

    private static String chatResponse(String response, String... dataSourcesUsed) {
        return ServerTestSupport.chatResponse(response, List.of(dataSourcesUsed));
    }

    /** A chat model that answers with {@code response} while citing the nth data source it was offered. */
    private static FakeChatModel citing(String response, int... dataSourceIndexes) {
        return new FakeChatModel(prompt -> {
            List<String> ids = dataSourceIdsIn(prompt);
            List<String> cited = new java.util.ArrayList<>();
            for (int index : dataSourceIndexes) {
                cited.add(ids.get(index));
            }
            return ServerTestSupport.chatResponse(response, cited);
        });
    }

    private void givenChunkText(Chunk chunk, String text) {
        datastore.writeString(chunk.textLocation(), text);
    }

    /**
     * Asserts the fragments appear in the prompt in the order given. The instruction prose is tuned
     * often, so these tests pin the layout the model is shown -- sources, then history, then the
     * question -- and leave the wording free to change.
     */
    private static void assertInOrder(String prompt, String... fragments) {
        int previousIndex = -1;
        String previousFragment = null;
        for (String fragment : fragments) {
            int index = prompt.indexOf(fragment);
            assertTrue(index >= 0, "missing from the prompt: " + fragment + "\n" + prompt);
            assertTrue(index > previousIndex,
                    "'" + fragment + "' should come after '" + previousFragment + "':\n" + prompt);
            previousIndex = index;
            previousFragment = fragment;
        }
    }

    // ----- happy path -------------------------------------------------------------------------

    @Test
    void returnsTheChatResponseAndTheSourceUrlItCited() {
        givenChunkText(CHUNK_A, "setbacks are 10 feet");
        QueryHandler handler = queryHandler(citing("Ten feet.", 0), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("what are the setbacks?", null), logger);

        assertEquals("Ten feet.", response.chatResponse());
        assertEquals(List.of("https://example.com/a"), response.sources());
    }

    @Test
    void citingSeveralChunksFromOneSourceYieldsOneUrl() {
        givenChunkText(CHUNK_A, "text a0");
        givenChunkText(chunk("a", 1), "text a1");
        QueryHandler handler = queryHandler(citing("Ten feet.", 0, 1), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A, chunk("a", 1)), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals(List.of("https://example.com/a"), response.sources(), "sources are de-duplicated");
    }

    @Test
    void citingChunksFromDifferentSourcesYieldsBothUrls() {
        givenChunkText(CHUNK_A, "text a");
        givenChunkText(CHUNK_B, "text b");
        QueryHandler handler = queryHandler(citing("Both.", 0, 1), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A, CHUNK_B), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals(2, response.sources().size(), response.sources().toString());
        assertTrue(response.sources().containsAll(List.of("https://example.com/a", "https://example.com/b")),
                response.sources().toString());
    }

    @Test
    void citedChunkIdsAreRecordedInSessionState() {
        givenChunkText(CHUNK_A, "text a");
        givenChunkText(CHUNK_B, "text b");
        QueryHandler handler = queryHandler(citing("Ten feet.", 1), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A, CHUNK_B), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        String sessionState = decryptedSessionState(response.sessionState());
        assertTrue(sessionState.contains("\"chunkIdsUsed\":[\"b:0\"]"),
                "the cited data-source id should be mapped back to its chunk id: " + sessionState);
    }

    @Test
    void dataSourceIdsHandedToTheModelAreRandomUuids() {
        givenChunkText(CHUNK_A, "setbacks are 10 feet");
        FakeChatModel chatModel = new FakeChatModel("not json");
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        handler.query(new Request("q", null), logger);

        String generatedId = dataSourceIdsIn(chatModel.lastPrompt()).get(0);
        assertTrue(generatedId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "data source ids should be random UUIDs, not guessable chunk ids: " + generatedId);
    }

    @Test
    void dataSourceIdsDifferBetweenRequests() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel firstModel = new FakeChatModel("not json");
        queryHandler(firstModel, new FakeEmbeddingModel(), new FakeVectorStore(List.of(CHUNK_A), List.of()),
                config(5, 5, 5)).query(new Request("q", null), logger);
        FakeChatModel secondModel = new FakeChatModel("not json");
        queryHandler(secondModel, new FakeEmbeddingModel(), new FakeVectorStore(List.of(CHUNK_A), List.of()),
                config(5, 5, 5)).query(new Request("q", null), logger);

        assertNotEquals(dataSourceIdsIn(firstModel.lastPrompt()), dataSourceIdsIn(secondModel.lastPrompt()),
                "a stable id across requests could be surmised and then hallucinated");
    }

    @Test
    void promptCarriesTheChunkTextTheUserPromptAndTheOutputFormatInThatOrder() {
        givenChunkText(CHUNK_A, "setbacks are 10 feet");
        FakeChatModel chatModel = new FakeChatModel("not json");
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        handler.query(new Request("what are the setbacks?", null), logger);

        String prompt = chatModel.lastPrompt();
        assertInOrder(prompt, "DATA SOURCES:", "setbacks are 10 feet", "PROMPT:", "what are the setbacks?",
                "\"dataSourcesUsed\"");
        assertTrue(prompt.indexOf("DATA SOURCES:") > 0, "instructions should precede the data sources: " + prompt);
    }

    @Test
    void chunkIdsAreNeverPutInThePrompt() {
        givenChunkText(CHUNK_A, "setbacks are 10 feet");
        FakeChatModel chatModel = new FakeChatModel("not json");
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        handler.query(new Request("q", null), logger);

        assertFalse(chatModel.lastPrompt().contains("a:0"),
                "a guessable chunk id in the prompt invites hallucinated citations");
    }

    // ----- vector search behaviour ------------------------------------------------------------

    @Test
    void searchesTwiceUsingTheConfiguredChunkCounts() {
        givenChunkText(CHUNK_A, "text a");
        FakeVectorStore vectorStore = new FakeVectorStore(List.of(CHUNK_A), List.of());
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                vectorStore, config(7, 3, 5));

        handler.query(new Request("q", null), logger);

        assertEquals(List.of(7, 3), vectorStore.topKsRequested,
                "first the conversation-wide search, then the most-recent-prompt search");
    }

    @Test
    void embedsTheConversationQueryThenTheUserPrompt() {
        givenChunkText(CHUNK_A, "text a");
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), embeddingModel,
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        handler.query(new Request("what are the setbacks?", null), logger);

        assertEquals(List.of("what are the setbacks?", "what are the setbacks?"), embeddingModel.embedded,
                "with no history the conversation query is just the user prompt");
    }

    @Test
    void deduplicatesChunksReturnedByBothSearches() {
        givenChunkText(CHUNK_A, "text a");
        givenChunkText(CHUNK_B, "text b");
        FakeChatModel chatModel = new FakeChatModel("not json");
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A, CHUNK_B), List.of(CHUNK_A)), config(5, 5, 5));

        handler.query(new Request("q", null), logger);

        assertEquals(2, dataSourceIdsIn(chatModel.lastPrompt()).size(), "chunk a appeared in both searches");
    }

    // ----- session state ----------------------------------------------------------------------

    @Test
    void aNullSessionStateStartsAFreshConversation() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel chatModel = new FakeChatModel("not json");
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertNotNull(response.sessionState());
        assertFalse(chatModel.lastPrompt().contains("RESPONSE:"), "there is no prior exchange to replay");
    }

    @Test
    void sessionStateWithoutExchangesIsTreatedAsEmpty() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", encryptedSessionState("{}")), logger);

        assertNotNull(response.sessionState(), "a session state with a null exchange list must not NPE");
    }

    @Test
    void sessionStateIsEncryptedInTheResponse() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("what are the setbacks?", null), logger);

        // the browser must never be handed readable conversation history
        assertFalse(response.sessionState().contains("promptExchanges"), response.sessionState());
        assertFalse(response.sessionState().contains("what are the setbacks?"), response.sessionState());
        assertTrue(decryptedSessionState(response.sessionState()).contains("promptExchanges"),
                "and it must still decrypt back to the state QueryHandler wrote");
    }

    @Test
    void aSessionStateThatCannotBeDecryptedFailsHard() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        // plaintext json is what a pre-encryption browser tab would still be holding
        assertThrows(RuntimeException.class,
                () -> handler.query(new Request("q", "{\"promptExchanges\":[]}"), logger),
                "session state that did not come from this server must not be accepted");
    }

    @Test
    void aSessionStateEncryptedWithAnotherKeyFailsHard() {
        givenChunkText(CHUNK_A, "text a");
        String foreignKey = Base64.getEncoder().encodeToString("a-different-32-byte-aes-key-here".getBytes(UTF_8));
        String forged = new EncryptionDelegate(foreignKey).encrypt("{\"promptExchanges\":[]}");
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        assertThrows(RuntimeException.class, () -> handler.query(new Request("q", forged), logger),
                "the GCM auth tag is what stops a client editing its own history");
    }

    @Test
    void aSuccessfulExchangeIsAppendedToSessionState() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel chatModel = new FakeChatModel(chatResponse("Ten feet."));
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("what are the setbacks?", null), logger);

        String sessionState = decryptedSessionState(response.sessionState());
        assertTrue(sessionState.contains("what are the setbacks?"), sessionState);
        assertTrue(sessionState.contains("Ten feet."), sessionState);
        assertTrue(sessionState.contains("a:0"), "the chunks offered should be recorded");
    }

    @Test
    void priorExchangesAreReplayedIntoTheNextPrompt() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel firstModel = new FakeChatModel(chatResponse("Ten feet."));
        QueryHandler firstHandler = queryHandler(firstModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));
        String sessionState = firstHandler.query(new Request("what are the setbacks?", null), logger).sessionState();

        FakeChatModel secondModel = new FakeChatModel(chatResponse("Yes."));
        FakeEmbeddingModel secondEmbeddingModel = new FakeEmbeddingModel();
        QueryHandler secondHandler = queryHandler(secondModel, secondEmbeddingModel,
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));
        secondHandler.query(new Request("is that from the property line?", sessionState), logger);

        String prompt = secondModel.lastPrompt();
        assertInOrder(prompt, "what are the setbacks?", "Ten feet.", "is that from the property line?");
        assertTrue(secondEmbeddingModel.embedded.get(0).contains("Ten feet."),
                "the conversation-wide search should embed the history too");
    }

    // ----- unknown cited id and parse failure handling ----------------------------------------------------

    @Test
    void anUnparseableChatResponseFallsBackWithoutThrowing() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel("I am not json at all"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals("Unable to parse response.", response.chatResponse());
        assertEquals(List.of(), response.sources());
        assertFalse(logger.errors.isEmpty(), "the parse failure should be logged");
    }

    @Test
    void aFailedExchangeIsNotAppendedToSessionState() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals("{\"promptExchanges\":[]}", decryptedSessionState(response.sessionState()));
    }

    @Test
    void aCitedIdThatWasNotInThePromptIsDroppedFromTheSourcesAndLogged() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel(chatResponse("Ten feet.", "invented-id")),
                new FakeEmbeddingModel(), new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals("Ten feet.", response.chatResponse());
        assertEquals(List.of(), response.sources(), "a citation nobody can follow is not shown to the reader");
        assertTrue(logger.messages.stream().anyMatch(message -> message.contains("invented-id")),
                "the dropped citation should still be logged: " + logger.messages);
    }

    @Test
    void aCitedIdThatWasNotInThePromptIsNotRecordedInSessionState() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel(chatResponse("Ten feet.", "invented-id")),
                new FakeEmbeddingModel(), new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        String sessionState = decryptedSessionState(response.sessionState());
        assertTrue(sessionState.contains("\"chunkIdsUsed\":[]"),
                "an id that maps to no chunk must not be carried into the next prompt: " + sessionState);
    }

    // The drop is per citation, not per response: one bad id must not cost the reader the real
    // sources cited alongside it.
    @Test
    void aRealCitationSurvivesAlongsideOneThatWasNotInThePrompt() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel chatModel = new FakeChatModel(prompt -> ServerTestSupport.chatResponse(
                "Ten feet.", List.of(dataSourceIdsIn(prompt).get(0), "invented-id")));
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals(List.of("https://example.com/a"), response.sources());
        assertTrue(decryptedSessionState(response.sessionState()).contains("\"chunkIdsUsed\":[\"a:0\"]"),
                decryptedSessionState(response.sessionState()));
    }

    @Test
    void aChunkIdInSessionStateThatNoLongerExistsIsLoggedAndSkipped() {
        givenChunkText(CHUNK_A, "text a");
        String sessionState = encryptedSessionState(JsonUtils.toJson(Map.of("promptExchanges", List.of(
                Map.of("prompt", "earlier", "response", "earlier answer",
                       "chunkIdsAvailable", List.of("gone:9"), "chunkIdsUsed", List.of("gone:9"))))));
        QueryHandler handler = queryHandler(new FakeChatModel("not json"), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", sessionState), logger);

        assertNotNull(response.chatResponse());
        assertTrue(logger.errors.stream().anyMatch(error -> error.contains("gone:9")),
                "a stale or hallucinated chunk id should be logged, not fatal: " + logger.errors);
    }

    // ----- previously-used chunk carry-over ----------------------------------------------------

    @Test
    void chunksUsedInAnEarlierTurnAreCarriedIntoTheNextPrompt() {
        givenChunkText(CHUNK_A, "text a");
        givenChunkText(CHUNK_B, "text b");
        String sessionState = encryptedSessionState(JsonUtils.toJson(Map.of("promptExchanges", List.of(
                Map.of("prompt", "earlier", "response", "earlier answer",
                       "chunkIdsAvailable", List.of("b:0"), "chunkIdsUsed", List.of("b:0"))))));
        FakeChatModel chatModel = new FakeChatModel("not json");
        FakeVectorStore vectorStore = new FakeVectorStore(List.of(CHUNK_A), List.of()).knowing(CHUNK_B);
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(), vectorStore, config(5, 5, 5));

        handler.query(new Request("q", sessionState), logger);

        String prompt = chatModel.lastPrompt();
        assertTrue(prompt.contains("text a"), prompt);
        assertTrue(prompt.contains("text b"), "the chunk cited last turn should still be available");
        assertEquals(List.of("b:0"), vectorStore.idsLookedUp);
    }

    @Test
    void carryOverIsCappedByTheConfiguredMaximum() {
        givenChunkText(CHUNK_A, "text a");
        for (int i = 1; i <= 3; i++) {
            givenChunkText(chunk("c", i), "carried text " + i);
        }
        String sessionState = encryptedSessionState(JsonUtils.toJson(Map.of("promptExchanges", List.of(
                Map.of("prompt", "earlier", "response", "earlier answer",
                       "chunkIdsAvailable", List.of("c:1", "c:2", "c:3"),
                       "chunkIdsUsed", List.of("c:1", "c:2", "c:3"))))));
        FakeChatModel chatModel = new FakeChatModel("not json");
        FakeVectorStore vectorStore = new FakeVectorStore(List.of(CHUNK_A), List.of())
                .knowing(chunk("c", 1), chunk("c", 2), chunk("c", 3));
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(), vectorStore, config(5, 5, 2));

        handler.query(new Request("q", sessionState), logger);

        String prompt = chatModel.lastPrompt();
        assertTrue(prompt.contains("carried text 1"), prompt);
        assertTrue(prompt.contains("carried text 2"), prompt);
        assertFalse(prompt.contains("carried text 3"), "the third carry-over exceeds the max of 2");
    }

    @Test
    void aChunkAlreadyFoundBySearchIsNotCarriedOverTwice() {
        givenChunkText(CHUNK_A, "text a");
        String sessionState = encryptedSessionState(JsonUtils.toJson(Map.of("promptExchanges", List.of(
                Map.of("prompt", "earlier", "response", "earlier answer",
                       "chunkIdsAvailable", List.of("a:0"), "chunkIdsUsed", List.of("a:0"))))));
        FakeChatModel chatModel = new FakeChatModel("not json");
        FakeVectorStore vectorStore = new FakeVectorStore(List.of(CHUNK_A), List.of());
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(), vectorStore, config(5, 5, 5));

        handler.query(new Request("q", sessionState), logger);

        assertEquals(1, dataSourceIdsIn(chatModel.lastPrompt()).size());
        assertEquals(List.of(), vectorStore.idsLookedUp, "a chunk already in the prompt needs no lookup");
    }

    // ----- response envelope -------------------------------------------------------------------

    @Test
    void detailsAreWithheldFromTheClient() {
        givenChunkText(CHUNK_A, "text a");
        FakeChatModel chatModel = new FakeChatModel(chatResponse("Ten feet."));
        QueryHandler handler = queryHandler(chatModel, new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertNull(response.details(),
                "details used to carry the raw prompt, which exposes the chunk text and the data-source ids");
    }

    @Test
    void aResponseCitingNothingHasNoSources() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel(chatResponse("I cannot answer that.")),
                new FakeEmbeddingModel(), new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        Response response = handler.query(new Request("q", null), logger);

        assertEquals("I cannot answer that.", response.chatResponse());
        assertEquals(List.of(), response.sources());
    }

    @Test
    void theQueryIsLogged() {
        givenChunkText(CHUNK_A, "text a");
        QueryHandler handler = queryHandler(new FakeChatModel(chatResponse("Ten feet.")), new FakeEmbeddingModel(),
                new FakeVectorStore(List.of(CHUNK_A), List.of()), config(5, 5, 5));

        handler.query(new Request("q", null), logger);

        assertTrue(logger.messages.stream().anyMatch(message -> message.startsWith("prompt: ")),
                logger.messages.toString());
    }

}
