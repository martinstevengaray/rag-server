package com.mgaray.ragserver.common;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.ssm.SsmClient;

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

}
