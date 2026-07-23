package com.mgaray.ragserver.server;

import com.mgaray.ragserver.common.JsonUtils;
import com.mgaray.ragserver.rag.QueryHandler;
import com.mgaray.ragserver.server.ServerModels.Request;
import com.mgaray.ragserver.server.ServerModels.Response;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WebappHandler implements JavaCoreServer.IListener {

    private final QueryHandler queryHandler;

    public WebappHandler(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        Request request = JsonUtils.toObject(body, Request.class);
        Response response = queryHandler.query(request);
        String responseJson = JsonUtils.toJson(response);
        System.out.println("Response: " + responseJson);
        return responseJson;
    }

    @Override
    public String handleGet(String path) {
        try(InputStream inputStream = getClass().getResourceAsStream("/index.html")) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
