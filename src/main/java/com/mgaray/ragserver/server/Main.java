package com.mgaray.ragserver.server;

public class Main implements JavaCoreServer.IListener {

    // curl "http://localhost/mypath"
    // curl -X POST -H "Content-Type: application/json" -d '{"username": "Bob", "password": "bob-secret"}' http://localhost/somepath

    public static void main(String[] args) {
        Main main = new Main();
        main.startService();
    }

    public void startService() {
        try {
            JavaCoreServer javaCoreServer = new JavaCoreServer();
            javaCoreServer.startServer(this, 80);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String handlePost(String path, String body) {
        System.out.println("Post: " + path + ", " + body);
        return "";
    }

    @Override
    public String handleGet(String path) {
        System.out.println("Get: " + path);
        return "";
    }

}
