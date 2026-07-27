package com.mgaray.ragserver.common;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SsmDelegate {

    private SsmClient ssmClient;

    public SsmDelegate () {
        this.ssmClient = SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build();
    }

    public String getParameter(String ssmParameterKey) {
        return ssmClient.getParameter(r -> r.name(ssmParameterKey).withDecryption(true))
                .parameter().value();
    }

    public void putParameter(String ssmParameterKey, String value) {
        ssmClient.putParameter(r -> r.name(ssmParameterKey).value(value).overwrite(true));
    }

    //convenience method when secrets are kept in local config file at "local/config.sh"
    public static String getParameterFromLocalConfig(String key) {
        try {
            List<String> lines = Files.readAllLines(Path.of("local/config.sh"));
            for (String line : lines) {
                String prefix = "export " + key + "=";
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).split("\"")[1];
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException(key +" not found in local/config.sh");
    }

}
