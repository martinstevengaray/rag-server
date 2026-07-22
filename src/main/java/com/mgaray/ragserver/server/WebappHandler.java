package com.mgaray.ragserver.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.rag.QueryHandler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WebappHandler implements JavaCoreServer.IListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final QueryHandler queryHandler;

    public WebappHandler(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    private record Request(String userPrompt, String sessionState) {}
    private record Response(String chatResponse, String sessionState, String rawPrompt) {}

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        Request request = JsonUtils.toObject(body, Request.class);
        String answer = this.queryHandler.query(request.userPrompt());
        Response response = new Response(answer, request.sessionState(), "debug-content-goes-here");
        String responseJson = JsonUtils.toJson(response);

        System.out.println("Response: " + responseJson);
        return responseJson;
    }

    @Override
    public String handleGet(String resource) {
        try(InputStream inputStream = getClass().getResourceAsStream("/index.html")) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
