package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.mgaray.ragserver.awsresources.Datastore;
import com.mgaray.ragserver.awsresources.IDatastore;
import com.mgaray.ragserver.awsresources.S3Datastore;
import com.mgaray.ragserver.common.AwsServicesDelegate;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.Chunk;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.Models.ChatModelType;
import com.mgaray.ragserver.common.Models.VectorQueryConfig;
import com.mgaray.ragserver.common.Models.Request;
import com.mgaray.ragserver.common.Models.Response;
import com.mgaray.ragserver.vectorstore.IVectorStore;
import com.mgaray.ragserver.vectorstore.S3VectorStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mgaray.ragserver.awsresources.Datastore.Mode.S3;
import static com.mgaray.ragserver.bootstrap.Embedder.createEmbeddingModel;
import static com.mgaray.ragserver.common.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;
import static com.mgaray.ragserver.server.QueryHandler.createChatModel;


public class LambdaServer implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final QueryHandler queryHandler;

    public LambdaServer() {
        String openAiKey = AwsServicesDelegate.fetchSmmParameterValue(
                System.getenv("OPEN_AI_API_KEY_SSM_PARAMETER_KEY"));
        String symmetricSigningKey = AwsServicesDelegate.fetchSmmParameterValue(
                System.getenv("SYMMETRIC_SIGNING_KEY_SSM_PARAMETER_KEY"));
        String chatModelTypeString = System.getenv("CHAT_MODEL_TYPE");
        String vectorQueryConfigJson = System.getenv("VECTOR_QUERY_CONFIG");
        String ingestionManifestBucket = System.getenv("INGESTION_MANIFEST_BUCKET");
        String vectorStoreBucket = System.getenv("VECTOR_STORE_BUCKET");
        String ingestionManifestId = System.getenv("INGESTION_MANIFEST_ID");

        VectorQueryConfig vectorQueryConfig = JsonUtils.toObject(vectorQueryConfigJson, VectorQueryConfig.class);
        ChatModelType chatModelType = ChatModelType.valueOf(chatModelTypeString);

        System.out.println("vectorQueryConfig: " + JsonUtils.toJson(vectorQueryConfig));

        WebappConfig webappConfig = new WebappConfig(chatModelType, vectorQueryConfig, openAiKey, symmetricSigningKey);
        IDatastore datastore = new S3Datastore(ingestionManifestBucket);
        IVectorStore<Chunk> vectorStore = new S3VectorStore<>(vectorStoreBucket, ingestionManifestId, Chunk.class);

        String ingestionManifestLocation = ingestManifestLocation(ingestionManifestId);
        IngestionManifest ingestionManifest = datastore.readObject(ingestionManifestLocation, IngestionManifest.class);
        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();

        this.queryHandler = new QueryHandler(webappConfig, datastore, vectorStore, embeddingSpec);
//        this.webappHandler = new WebappHandler(queryHandler);

//public QueryHandler(WebappConfig webappConfig,
//                IDatastore datastore,
//                IVectorStore< Models.Chunk > vectorStore,
//                String sourceManifestId) {
        System.out.println( //"openAiKey: " + openAiKey + "," +
                            //"symmetricSigningKey: " + symmetricSigningKey + ", " +
                            "chatModelTypeString: " + chatModelTypeString + ", " +
                            "vectorQueryConfigJson: " + vectorQueryConfigJson + ", " +
                            "ingestionManifestBucket: " + ingestionManifestBucket + ", " +
                            "vectorStoreBucket: " + vectorStoreBucket + ", " +
                            "ingestionManifestId: " + ingestionManifestId);

//        String ingestionManifestLocation = ingestManifestLocation(ingestionManifestId);
//        byte[] ingestionManifestBytes = S3Utils.readBytes(ingestionManifestBucket, ingestionManifestLocation);
//        String ingestionManifestString  = new String(ingestionManifestBytes, StandardCharsets.UTF_8);
//        IngestionManifest ingestionManifest = JsonUtils.toObject(ingestionManifestString, IngestionManifest.class);
        System.out.println(JsonUtils.toJson(ingestionManifest));
        EmbeddingModel embeddingModel = createEmbeddingModel(ingestionManifest.runDefinition().embeddingSpec(), openAiKey);

        float[] chunkEmbedding = embeddingModel.embed("embed this piece of text").content().vector();
        System.out.println("chunkEmbedding: " + chunkEmbedding);

        ChatModel chatModel = createChatModel(new WebappConfig(OPEN_AI_GPT_4O_MINI, vectorQueryConfig, openAiKey, symmetricSigningKey));
        String chatResult = chatModel.chat("What is vitamin D used for");
        System.out.println("chatResult: " + chatResult);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        System.out.println(JsonUtils.toJson(input));

        String method = extractMethod(input);        //POST OR GET
        String path = extractPath(input);
        System.out.println("method=" + method + ", path=" + path);
        String responseString = null;
        if ("GET".equals(method)) {
            responseString = this.handleGet(path);
            return proxyResponseHtml(200, responseString);
        } else if ("POST".equals(method)) {
            responseString = this.handlePost(path, extractBody(input));
            return proxyResponseJson(200, responseString);
        } else {
            Request request = extractRequest(input);
            System.out.println(JsonUtils.toJson(request));

            Response response = new Response("chatResponse", List.of("source1", "source2"), "sessionState", "details");
            System.out.println(JsonUtils.toJson(response));

            return proxyResponseJson(200, JsonUtils.toJson(response));
        }
    }

    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        Request request = JsonUtils.toObject(body, Request.class);
        Response response = queryHandler.query(request);
        String responseJson = JsonUtils.toJson(response);
        System.out.println("Response: " + responseJson);
        return responseJson;
    }

    public String handleGet(String path) {
        try(InputStream inputStream = getClass().getResourceAsStream("/index.html")) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Handles REST API (v1: "httpMethod") and HTTP API v2 / Function URL ("requestContext.http.method"). */
    private static String extractMethod(Map<String, Object> input) {
        String method = (String) input.get("httpMethod");
        if (method == null) {
            method = JsonUtils.getNestedField(input, "requestContext", "http", "method");
        }
        return method;
    }

    /**
     * Handles REST API (v1: "path") and HTTP API v2 / Function URL ("rawPath"). Falls back to "/"
     * so handlers always get a usable path; the query string is deliberately excluded.
     */
    private static String extractPath(Map<String, Object> input) {
        String path = (String) input.get("path");
        if (path == null) {
            path = (String) input.get("rawPath");
        }
        if (path == null) {
            path = JsonUtils.getNestedField(input, "requestContext", "http", "path");
        }
        return (path != null && !path.isBlank()) ? path : "/";
    }

    /**
     * The proxy "body" is always a JSON string (optionally base64-encoded), never a nested object,
     * so it is passed through as-is rather than re-serialized.
     */
    private static String extractBody(Map<String, Object> input) {
        String body = (String) input.get("body");
        if (body == null || body.isBlank()) {
            return null;
        }
        if (Boolean.TRUE.equals(input.get("isBase64Encoded"))) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return body;
    }

    private static Request extractRequest(Map<String, Object> input) {
        String body = extractBody(input);
        return (body != null) ? JsonUtils.toObject(body, Request.class) : null;
    }

    /** Proxy integrations require a {statusCode, headers, body} envelope with body as a JSON string. */
    private static Map<String, Object> proxyResponseJson(int statusCode, String body) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", Map.of(
                "Content-Type", "application/json; charset=utf-8",
                "Access-Control-Allow-Origin", "*"));
        result.put("body", body);
        result.put("isBase64Encoded", false);
        return result;
    }

    private static Map<String, Object> proxyResponseHtml(int statusCode, String body) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", Map.of(
                "Content-Type", "text/html; charset=utf-8",
                "Access-Control-Allow-Origin", "*"));
        result.put("body", body);
        result.put("isBase64Encoded", false);
        return result;
    }

}
