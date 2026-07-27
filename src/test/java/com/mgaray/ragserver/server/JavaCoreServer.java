package com.mgaray.ragserver.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class JavaCoreServer {

    public interface IListener {
        String handlePost(String path, String body);
        String handleGet(String path);
    }

    public JavaCoreServer() {}

    public void startServer(IListener iListener, int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MyHandler(iListener));
        server.start();
    }

    private static class MyHandler implements HttpHandler {
        IListener iListener;
        MyHandler(IListener iListener) {
            this.iListener = iListener;
        }

        @Override
        public void handle(HttpExchange httpExchange)  {
            try {
                String method = httpExchange.getRequestMethod();
                String path = httpExchange.getRequestURI().getPath();
                String body = new String(httpExchange.getRequestBody().readAllBytes());
                httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                byte[] response = new byte[0];
                switch (method) {
                    case "OPTIONS": //cors
                        httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                        httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, x-ijt"); //the "x-ijt" header is used by intelliJ
                        httpExchange.sendResponseHeaders(204, -1);
                        break;
                    case "POST":
                        httpExchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                        response = iListener.handlePost(path, body).getBytes(StandardCharsets.UTF_8);
                        httpExchange.sendResponseHeaders(200, response.length);
                        break;
                    case "GET":
                        httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                        response = iListener.handleGet(path).getBytes(StandardCharsets.UTF_8);
                        httpExchange.sendResponseHeaders(200, response.length);
                        break;
                }
                OutputStream os = httpExchange.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
//NOTE: sendResponseHeaders() must be called after setting the headers and before writing the body

