package com.mgaray.ragserver.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mgaray.ragserver.rag.QueryHandler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class WebappHandler implements JavaCoreServer.IListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final QueryHandler queryHandler;

    public WebappHandler(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        try {
            // Parse the JSON request from the client: { "query": "...", "sessionState": "..." | null }
            JsonNode request = OBJECT_MAPPER.readTree(body);
            String query = request.path("query").asText();

            // The client sends null on its first request and echoes back whatever we
            // returned on subsequent requests. Mint a new session id when it is absent.
            JsonNode sessionStateNode = request.path("sessionState");
            String sessionState = sessionStateNode.isMissingNode() || sessionStateNode.isNull()
                    ? UUID.randomUUID().toString()
                    : sessionStateNode.asText();

            String answer = this.queryHandler.query(query);

            // Build the JSON response: { "length": N, "content": "...", "sessionState": "...", "debug": "..." | null }.
            // Jackson handles all string escaping, so no manual HTML/JSON escaping is needed.
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("length", query.length());
            response.put("content", answer);
            response.put("sessionState", sessionState);
            // "debug" carries an arbitrary diagnostic string; may be null when there is nothing to report.
            response.put("debug", "query.length=" + query.length() + ", sessionState=" + sessionState);
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process request JSON: " + body, e);
        }
    }

    @Override
    public String handleGet(String path) {
        String resource = path.equals("/") ? "/index.html" : path;
        return readResource(resource);
    }

    //reads a page from the resources folder (classpath)
    private String readResource(String resource) {
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                return "<html><body>404 - " + resource + " not found</body></html>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
