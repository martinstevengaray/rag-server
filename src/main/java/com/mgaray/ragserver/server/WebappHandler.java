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

    private record Request(String query, String sessionState) {}
    private record Response(String content, String sessionState, String debug) {}

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        Request request = JsonUtils.toObject(body, Request.class);
        String answer = this.queryHandler.query(request.query);
        Response response = new Response(answer, request.sessionState(), "debug-content-goes-here");
        return JsonUtils.toJson(response);
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
