package com.mgaray.ragserver.localserver;

import com.mgaray.ragserver.logger.Logger;
import com.mgaray.ragserver.server.LambdaServer;
import com.mgaray.ragserver.util.JsonUtils;
import com.mgaray.ragserver.Models.Request;
import com.mgaray.ragserver.Models.Response;
import com.mgaray.ragserver.server.QueryHandler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WebappHandler implements LocalServer.IListener {

    private final QueryHandler queryHandler;
    private final Logger logger = new Logger();

    public WebappHandler(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @Override
    public String handlePost(String path, String body) {
        logger.log("Post: " + path + ", " + body);
        Request request = JsonUtils.toObject(body, Request.class);
        Response response = queryHandler.query(request, logger);
        String responseJson = JsonUtils.toJson(response);
        logger.log("Response: " + responseJson);
        return responseJson;
    }

    // null for anything outside the served set, which the caller turns into a 404. Passing the
    // unresolved null straight to getResourceAsStream would throw instead.
    @Override
    public String handleGet(String path) {
        String resource = LambdaServer.resolveResource(path);
        if (resource == null) {
            return null;
        }
        try(InputStream inputStream = getClass().getResourceAsStream(resource)) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
