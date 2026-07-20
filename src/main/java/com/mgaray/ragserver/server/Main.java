package com.mgaray.ragserver.server;

public class Main {

    // curl "http://localhost/mypath"
    // curl -X POST -H "Content-Type: application/json" -d '{"username": "Bob", "password": "bob-secret"}' http://localhost/somepath

    public static void main(String[] args) {
        Main main = new Main();
        main.startService();
    }

    public void startService() {
        try {
            JavaCoreServer javaCoreServer = new JavaCoreServer();
            javaCoreServer.startServer(new WebappHandler(), 80);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
