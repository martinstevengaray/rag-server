package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.mgaray.ragserver.common.AwsServicesDelegate;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.common.Models.IngestionManifest;
import com.mgaray.ragserver.common.Models.WebappConfig;
import com.mgaray.ragserver.common.Models.EmbeddingSpec;
import com.mgaray.ragserver.common.S3Utils;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mgaray.ragserver.bootstrap.Embedder.createEmbeddingModel;
import static com.mgaray.ragserver.common.Models.ChatModelType.OPEN_AI_GPT_4O_MINI;
import static com.mgaray.ragserver.common.Models.ingestManifestLocation;
import static com.mgaray.ragserver.rag.QueryHandler.createChatModel;


public class JavaLambdaServer implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public JavaLambdaServer() {
        String openAiKey = AwsServicesDelegate.fetchSmmParameterValue(
                System.getenv("OPEN_AI_API_KEY_SSM_PARAMETER_KEY"));
        String symmetricSigningKey = AwsServicesDelegate.fetchSmmParameterValue(
                System.getenv("SYMMETRIC_SIGNING_KEY_SSM_PARAMETER_KEY"));
        String chatModelTypeString = System.getenv("CHAT_MODEL_TYPE");
        String chunksToProvideString = System.getenv("CHUNKS_TO_PROVIDE");
        String ingestionManifestBucket = System.getenv("INGESTION_MANIFEST_BUCKET");
        String vectorStoreBucket = System.getenv("VECTOR_STORE_BUCKET");
        String ingestionManifestId = System.getenv("INGESTION_MANIFEST_ID");

        System.out.println( //"openAiKey: " + openAiKey + "," +
                            //"symmetricSigningKey: " + symmetricSigningKey + ", " +
                            "chatModelTypeString: " + chatModelTypeString + ", " +
                            "chunksToProvideString: " + chunksToProvideString + ", " +
                            "ingestionManifestBucket: " + ingestionManifestBucket + ", " +
                            "vectorStoreBucket: " + vectorStoreBucket + ", " +
                            "ingestionManifestId: " + ingestionManifestId);

        String ingestionManifestLocation = ingestManifestLocation(ingestionManifestId);
        byte[] ingestionManifestBytes = S3Utils.readBytes(ingestionManifestBucket, ingestionManifestLocation);
        String ingestionManifestString  = new String(ingestionManifestBytes, StandardCharsets.UTF_8);
        IngestionManifest ingestionManifest = JsonUtils.toObject(ingestionManifestString, IngestionManifest.class);
        System.out.println(JsonUtils.toJson(ingestionManifest));
        EmbeddingModel embeddingModel = createEmbeddingModel(ingestionManifest.runDefinition().embeddingSpec(), openAiKey);

        float[] chunkEmbedding = embeddingModel.embed("embed this piece of text").content().vector();
        System.out.println("chunkEmbedding: " + chunkEmbedding);

        ChatModel chatModel = createChatModel(new WebappConfig(OPEN_AI_GPT_4O_MINI, 20, openAiKey, symmetricSigningKey));
        String chatResult = chatModel.chat("What is vitamin D used for");
        System.out.println("chatResult: " + chatResult);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        System.out.println(JsonUtils.toJson(input));

        String method = extractMethod(input);        //POST OR GET
        System.out.println("method=" + method);
        Request request = extractRequest(input);
        System.out.println(JsonUtils.toJson(request));

        Response response = new Response("chatResponse", List.of("source1", "source2"), "sessionState", "details");
        System.out.println(JsonUtils.toJson(response));

        return proxyResponse(200, JsonUtils.toJson(response));
    }

    /** Handles REST API (v1: "httpMethod") and HTTP API v2 / Function URL ("requestContext.http.method"). */
    private static String extractMethod(Map<String, Object> input) {
        String method = (String) input.get("httpMethod");
        if (method == null) {
            method = JsonUtils.getNestedField(input, "requestContext", "http", "method");
        }
        return method;
    }

    /** The proxy "body" is always a JSON string (optionally base64-encoded), never a nested object. */
    private static Request extractRequest(Map<String, Object> input) {
        String body = (String) input.get("body");
        if (body == null || body.isBlank()) {
            return null;
        }
        if (Boolean.TRUE.equals(input.get("isBase64Encoded"))) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return JsonUtils.toObject(body, Request.class);
    }

    /** Proxy integrations require a {statusCode, headers, body} envelope with body as a JSON string. */
    private static Map<String, Object> proxyResponse(int statusCode, String body) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", Map.of(
                "Content-Type", "application/json; charset=utf-8",
                "Access-Control-Allow-Origin", "*"));
        result.put("body", body);
        result.put("isBase64Encoded", false);
        return result;
    }

}
