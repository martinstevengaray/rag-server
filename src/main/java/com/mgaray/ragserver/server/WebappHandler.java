package com.mgaray.ragserver.server;

import com.mgaray.ragserver.rag.QueryHandler;

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
        String response = this.queryHandler.query(body);
        return readResource("/confirm.html")
                .replace("{{length}}", String.valueOf(body.length()))
                .replace("{{content}}", escapeHtml(response));
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

    //escapes user text so it can't break out of the html we render it into
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
