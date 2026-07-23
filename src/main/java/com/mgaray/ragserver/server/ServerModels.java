package com.mgaray.ragserver.server;

import java.util.List;

public class ServerModels {

    public record Request(String userPrompt, String sessionState) {}

    public record Response(String chatResponse, List<String> sources, String sessionState, String details) {}

}
