package com.mgaray.ragserver.server;

public class ServerModels {


    public record Request(String userPrompt, String sessionState) {}

    public record Response(String chatResponse, String sessionState, String details) {}

}
