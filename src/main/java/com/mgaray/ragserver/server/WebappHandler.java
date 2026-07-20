package com.mgaray.ragserver.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WebappHandler implements JavaCoreServer.IListener{


    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        return "Received " + body.length() + " characters";
    }

    @Override
    public String handleGet(String path) {
        return readHtmlPage(path);
    }

    //reads an html page from the resources folder (classpath); "/" maps to index.html
    private String readHtmlPage(String path) {
        try {
            String resource = path.equals("/") ? "/index.html" : path;
            try (InputStream is = getClass().getResourceAsStream(resource)) {
                if (is == null) {
                    return "<html><body>404 - " + resource + " not found</body></html>";
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
