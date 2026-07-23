package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JavaLambdaServer implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        System.out.println(JsonUtils.toJsonPretty(input));

        String method = extractMethod(input);        //POST OR GET
        System.out.println("method=" + method);
        Request request = extractRequest(input);
        System.out.println(JsonUtils.toJsonPretty(request));

        Response response = new Response("chatResponse", List.of("source1", "source2"), "sessionState", "details");
        System.out.println(JsonUtils.toJsonPretty(response));

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
