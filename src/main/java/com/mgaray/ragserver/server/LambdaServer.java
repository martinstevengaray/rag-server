package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.mgaray.ragserver.logger.ILogger;
import com.mgaray.ragserver.logger.Logger;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.S3Datastore;
import com.mgaray.ragserver.storage.parameter.SsmDelegate;
import com.mgaray.ragserver.util.JsonUtils;
import com.mgaray.ragserver.Models.IngestionManifest;
import com.mgaray.ragserver.Models.WebappConfig;
import com.mgaray.ragserver.Models.Chunk;
import com.mgaray.ragserver.Models.EmbeddingSpec;
import com.mgaray.ragserver.Models.ChatModelType;
import com.mgaray.ragserver.Models.VectorQueryConfig;
import com.mgaray.ragserver.Models.Request;
import com.mgaray.ragserver.Models.Response;
import com.mgaray.ragserver.storage.vector.IVectorStore;
import com.mgaray.ragserver.storage.vector.S3VectorStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class LambdaServer implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final QueryHandler queryHandler;

    public LambdaServer() {
        Timer timer = new Timer();
        SsmDelegate ssmDelegate = new SsmDelegate();
        String openAiKey = ssmDelegate.getParameter(System.getenv("OPEN_AI_API_KEY_SSM_PARAMETER_KEY"));
        String symmetricSigningKey =
                ssmDelegate.getParameter(System.getenv("SYMMETRIC_SIGNING_KEY_SSM_PARAMETER_KEY"));
        String chatModelTypeString = System.getenv("CHAT_MODEL_TYPE");
        String vectorQueryConfigJson = System.getenv("VECTOR_QUERY_CONFIG");
        String ingestionManifestBucket = System.getenv("INGESTION_MANIFEST_BUCKET");
        String vectorStoreBucket = System.getenv("VECTOR_STORE_BUCKET");
        String ingestionManifestId = System.getenv("INGESTION_MANIFEST_ID");
        VectorQueryConfig vectorQueryConfig = JsonUtils.toObject(vectorQueryConfigJson, VectorQueryConfig.class);
        ChatModelType chatModelType = ChatModelType.valueOf(chatModelTypeString);
        WebappConfig webappConfig = new WebappConfig(chatModelType, vectorQueryConfig, openAiKey, symmetricSigningKey);
        IDatastore datastore = new S3Datastore(ingestionManifestBucket);
        IVectorStore<Chunk> vectorStore = new S3VectorStore<>(vectorStoreBucket, ingestionManifestId, Chunk.class);
        IngestionManifest ingestionManifest = datastore.readIngestionManifest(ingestionManifestId);
        EmbeddingSpec embeddingSpec = ingestionManifest.runDefinition().embeddingSpec();
        this.queryHandler = new QueryHandler(webappConfig, datastore, vectorStore, embeddingSpec);
        timer.snap("LambdaServer construction");
    }

    //to support unit tests
    LambdaServer(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        ILogger logger = new Logger(context);
        logger.log("input", input);
        String method = extractMethod(input);        //POST OR GET
        String path = extractPath(input);
        logger.log("method=" + method + ", path=" + path);
        String responseString = null;
        if ("GET".equals(method)) {
            responseString = this.handleGet(path);
            return responseString == null ?
                    proxyResponseNoContent() :
                    proxyResponseHtml(200, responseString);
        } else if ("POST".equals(method)) {
            String body = extractBody(input);
            logger.log("body=" + body);
            responseString = this.handlePost(path, body, logger);
            return proxyResponseJson(200, responseString);
        } else {
            Request request = extractRequest(input);
            logger.log("request", request);
            return proxyResponseJson(200, "{}");
        }
    }

    public String handlePost(String path, String body, ILogger logger) {
        Request request = JsonUtils.toObject(body, Request.class);
        Response response = queryHandler.query(request, logger);
        String responseJson = JsonUtils.toJson(response);
        return responseJson;
    }

    public String handleGet(String path) {
        if (!"/".equals(path)) {
            return null;
        }
        try(InputStream inputStream = getClass().getResourceAsStream("/index.html")) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractMethod(Map<String, Object> input) {
        String method = (String) input.get("httpMethod");
        if (method == null) {
            method = JsonUtils.getNestedField(input, "requestContext", "http", "method");
        }
        return method;
    }

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

    private static Map<String, Object> proxyResponseJson(int statusCode, String body) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", Map.of(
                "Content-Type", "application/json; charset=utf-8"));
        result.put("body", body);
        result.put("isBase64Encoded", false);
        return result;
    }

    private static Map<String, Object> proxyResponseHtml(int statusCode, String body) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", Map.of(
                "Content-Type", "text/html; charset=utf-8"));
        result.put("body", body);
        result.put("isBase64Encoded", false);
        return result;
    }

    private static Map<String, Object> proxyResponseNoContent() {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", 204);
        result.put("headers", Map.of(
                "Cache-Control", "public, max-age=86400"));
        result.put("body", "");
        result.put("isBase64Encoded", false);
        return result;
    }

}
