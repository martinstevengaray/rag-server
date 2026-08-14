package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
import com.mgaray.ragserver.storage.data.InMemoryDatastore;
import com.mgaray.ragserver.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.mgaray.ragserver.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.server.ServerTestSupport.SIGNING_KEY;
import static com.mgaray.ragserver.server.ServerTestSupport.chunk;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaServerTest {

    private static final Chunk CHUNK_A = chunk("a", 0);

    /** The minimum Lambda {@link Context} needed for {@code handleRequest} to build its Logger. */
    private static final Context CONTEXT = new Context() {
        @Override public String getAwsRequestId() { return "test-request-id"; }
        @Override public String getLogGroupName() { return "test-log-group"; }
        @Override public String getLogStreamName() { return "test-log-stream"; }
        @Override public String getFunctionName() { return "rag-server"; }
        @Override public String getFunctionVersion() { return "1"; }
        @Override public String getInvokedFunctionArn() { return "arn:test"; }
        @Override public CognitoIdentity getIdentity() { return null; }
        @Override public ClientContext getClientContext() { return null; }
        @Override public int getRemainingTimeInMillis() { return 30_000; }
        @Override public int getMemoryLimitInMB() { return 512; }
        @Override public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override public void log(String message) {}
                @Override public void log(byte[] message) {}
            };
        }
    };

    private final InMemoryDatastore datastore = new InMemoryDatastore();
    private final RecordingLogger logger = new RecordingLogger();

    /** A LambdaServer wired to a QueryHandler whose models are fakes, so nothing leaves the process. */
    private LambdaServer lambdaServer(FakeChatModel chatModel) {
        datastore.writeString(CHUNK_A.textLocation(), "setbacks are 10 feet");
        WebappConfig webappConfig = new WebappConfig(
                OPEN_AI_GPT_4O_MINI, new VectorQueryConfig(5, 5, 5), "unused-key", SIGNING_KEY);
        QueryHandler queryHandler = new QueryHandler(webappConfig, datastore,
                new FakeVectorStore(List.of(CHUNK_A), List.of()), new FakeEmbeddingModel(), chatModel,
                new EncryptionDelegate(SIGNING_KEY));
        return new LambdaServer(queryHandler);
    }

    private LambdaServer lambdaServer() {
        return lambdaServer(new FakeChatModel(ServerTestSupport.chatResponse("Ten feet.", List.of())));
    }

    private static Map<String, Object> restRequest(String method, String path, String body) {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("httpMethod", method);
        input.put("path", path);
        input.put("body", body);
        return input;
    }

    /** API Gateway payload format 2.0, where method and path live under requestContext.http. */
    private static Map<String, Object> httpApiRequest(String method, String path, String body) {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("rawPath", path);
        input.put("body", body);
        input.put("requestContext", Map.of("http", Map.of("method", method, "path", path)));
        return input;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headersOf(Map<String, Object> response) {
        return (Map<String, String>) response.get("headers");
    }

    // ----- GET --------------------------------------------------------------------------------

    @Test
    void getServesTheBundledWebapp() {
        Map<String, Object> response = lambdaServer().handleRequest(restRequest("GET", "/", null), CONTEXT);

        assertEquals(200, response.get("statusCode"));
        assertTrue(((String) response.get("body")).contains("<html"), "expected index.html to be served");
    }

    @Test
    void getServesTheWidgetScript() {
        Map<String, Object> response = lambdaServer().handleRequest(restRequest("GET", "/widget.js", null), CONTEXT);

        assertEquals(200, response.get("statusCode"));
        assertEquals("text/javascript; charset=utf-8", headersOf(response).get("Content-Type"));
        assertTrue(((String) response.get("body")).contains("data-rag-chat"),
                "expected widget.js to be served");
    }

    // The bundled page must not carry its own copy of the client: it mounts the same widget an
    // embedding site would, so there is a single implementation to keep working.
    @Test
    void theBundledWebappLoadsTheWidgetRatherThanInliningIt() {
        String body = (String) lambdaServer().handleRequest(restRequest("GET", "/", null), CONTEXT).get("body");

        assertTrue(body.contains("/widget.js"), "expected index.html to load the widget");
        assertTrue(body.contains("data-rag-chat"), "expected index.html to provide a mount point");
        assertFalse(body.contains("fetch("), "expected no inlined client logic in index.html");
    }

    @Test
    void getIsServedAsHtml() {
        Map<String, Object> response = lambdaServer().handleRequest(restRequest("GET", "/", null), CONTEXT);

        assertEquals("text/html; charset=utf-8", headersOf(response).get("Content-Type"));
        assertEquals(false, response.get("isBase64Encoded"));
    }

    @Test
    void getIsRecognisedFromTheHttpApiPayloadShape() {
        Map<String, Object> response = lambdaServer().handleRequest(httpApiRequest("GET", "/", null), CONTEXT);

        assertEquals(200, response.get("statusCode"));
        assertEquals("text/html; charset=utf-8", headersOf(response).get("Content-Type"));
    }

    @Test
    void handleGetReadsTheIndexResourceDirectly() {
        String body = lambdaServer().handleGet("/");

        assertTrue(body.contains("<html"), body.substring(0, Math.min(200, body.length())));
    }

    // ----- POST -------------------------------------------------------------------------------

    @Test
    void postAnswersWithTheQueryResponseAsJson() {
        Map<String, Object> input = restRequest("POST", "/",
                JsonUtils.toJson(new Request("what are the setbacks?", null)));

        Map<String, Object> response = lambdaServer().handleRequest(input, CONTEXT);

        assertEquals(200, response.get("statusCode"));
        assertEquals("application/json; charset=utf-8", headersOf(response).get("Content-Type"));
        assertEquals(false, response.get("isBase64Encoded"));
    }

    @Test
    void postBodyIsParsedIntoTheRequestHandedToTheQueryHandler() {
        FakeChatModel chatModel = new FakeChatModel(ServerTestSupport.chatResponse("Ten feet.", List.of()));
        Map<String, Object> input = restRequest("POST", "/",
                JsonUtils.toJson(new Request("what are the setbacks?", null)));

        lambdaServer(chatModel).handleRequest(input, CONTEXT);

        assertTrue(chatModel.lastPrompt().trim().endsWith("what are the setbacks?"),
                "the user prompt should have reached the model: " + chatModel.lastPrompt());
    }

    @Test
    void postResponseBodyDeserialisesBackIntoAResponse() {
        Map<String, Object> input = restRequest("POST", "/", JsonUtils.toJson(new Request("q", null)));

        Map<String, Object> proxyResponse = lambdaServer().handleRequest(input, CONTEXT);

        Response response = JsonUtils.toObject((String) proxyResponse.get("body"), Response.class);
        assertEquals("Ten feet.", response.chatResponse());
        assertEquals(List.of(), response.sources());
    }

    @Test
    void aBase64EncodedPostBodyIsDecoded() {
        String json = JsonUtils.toJson(new Request("what are the setbacks?", null));
        Map<String, Object> input = restRequest("POST", "/",
                Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        input.put("isBase64Encoded", true);
        FakeChatModel chatModel = new FakeChatModel(ServerTestSupport.chatResponse("Ten feet.", List.of()));

        lambdaServer(chatModel).handleRequest(input, CONTEXT);

        assertTrue(chatModel.lastPrompt().trim().endsWith("what are the setbacks?"),
                "a base64 body should be decoded before parsing: " + chatModel.lastPrompt());
    }

    @Test
    void aPlainPostBodyIsNotBase64Decoded() {
        Map<String, Object> input = restRequest("POST", "/", JsonUtils.toJson(new Request("q", null)));
        input.put("isBase64Encoded", false);

        Map<String, Object> response = lambdaServer().handleRequest(input, CONTEXT);

        assertEquals(200, response.get("statusCode"));
    }

    @Test
    void postIsRecognisedFromTheHttpApiPayloadShape() {
        Map<String, Object> input = httpApiRequest("POST", "/", JsonUtils.toJson(new Request("q", null)));

        Map<String, Object> response = lambdaServer().handleRequest(input, CONTEXT);

        assertEquals("application/json; charset=utf-8", headersOf(response).get("Content-Type"));
    }

    @Test
    void handlePostReturnsTheSerialisedResponseDirectly() {
        String body = lambdaServer().handlePost("/", JsonUtils.toJson(new Request("q", null)), logger);

        Response response = JsonUtils.toObject(body, Response.class);
        assertEquals("Ten feet.", response.chatResponse());
    }

    // ----- other methods ----------------------------------------------------------------------

    @Test
    void anUnsupportedMethodReturnsAnEmptyJsonBody() {
        Map<String, Object> response =
                lambdaServer().handleRequest(restRequest("DELETE", "/", null), CONTEXT);

        assertEquals(200, response.get("statusCode"));
        assertEquals("{}", response.get("body"));
        assertEquals("application/json; charset=utf-8", headersOf(response).get("Content-Type"));
    }

    @Test
    void aMissingMethodDoesNotReachTheQueryHandler() {
        FakeChatModel chatModel = new FakeChatModel(ServerTestSupport.chatResponse("Ten feet.", List.of()));
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("path", "/");

        Map<String, Object> response = lambdaServer(chatModel).handleRequest(input, CONTEXT);

        assertEquals("{}", response.get("body"));
        assertTrue(chatModel.prompts.isEmpty(), "no method means no query");
    }

    @Test
    void anUnsupportedMethodWithABodyStillReturnsEmptyJson() {
        Map<String, Object> input = restRequest("PUT", "/", JsonUtils.toJson(new Request("q", null)));

        Map<String, Object> response = lambdaServer().handleRequest(input, CONTEXT);

        assertEquals("{}", response.get("body"));
    }

    // ----- response envelope -------------------------------------------------------------------

    // The function URL's cors{} block adds Access-Control-Allow-Origin itself. If the handler
    // sets it too the header goes out twice and browsers reject the response, so no response
    // may carry its own.
    @Test
    void noResponseSetsItsOwnCorsHeader() {
        List<Map<String, Object>> responses = List.of(
                lambdaServer().handleRequest(restRequest("GET", "/", null), CONTEXT),
                lambdaServer().handleRequest(restRequest("POST", "/", JsonUtils.toJson(new Request("q", null))),
                        CONTEXT),
                lambdaServer().handleRequest(restRequest("DELETE", "/", null), CONTEXT));

        for (Map<String, Object> response : responses) {
            assertFalse(headersOf(response).containsKey("Access-Control-Allow-Origin"));
            assertEquals(false, response.get("isBase64Encoded"));
        }
    }

    @Test
    void theProxyResponseHasTheKeysApiGatewayRequires() {
        Map<String, Object> response = lambdaServer().handleRequest(restRequest("GET", "/", null), CONTEXT);

        assertTrue(response.keySet().containsAll(List.of("statusCode", "headers", "body", "isBase64Encoded")),
                response.keySet().toString());
        assertFalse(response.containsKey("multiValueHeaders"));
    }

}
