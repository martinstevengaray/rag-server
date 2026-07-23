package com.mgaray.ragserver.server;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;

import java.util.List;
import java.util.Map;


public class JavaLambdaServer implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        System.out.println(JsonUtils.toJsonPretty(input));
        Request request = JsonUtils.toObject(JsonUtils.toJson(input.get("body")), Request.class);
        String method = (String)input.get("method"); //POST OR GET
        System.out.println(JsonUtils.toJsonPretty(request));
        Response response = new Response("chatResponse", List.of("source1","source2"), "sessionState", "details");
        System.out.println(JsonUtils.toJsonPretty(response));
        return JsonUtils.parse(JsonUtils.toJson(response));
    }

}
