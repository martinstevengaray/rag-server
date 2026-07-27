package com.mgaray.ragserver.common.notused;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class AwsServicesDelegate {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static String fetchSmmParameterValue(String ssmParameterKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:2773/systemsmanager/parameters/get"
                                + "?withDecryption=true&name=" + urlEncode(ssmParameterKey)))
                .header("X-Aws-Parameters-Secrets-Token", System.getenv("AWS_SESSION_TOKEN"))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("reading " + ssmParameterKey
                    + " from parameter store failed: HTTP " + response.statusCode());
        }
        return JsonUtilsAll.getNestedField(response.body(), "Parameter", "Value");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Direct SSM client for mutable state: the extension layer above caches reads
    // (which would defeat duplicate-tick detection) and cannot write at all.
    private static SsmClient ssmClient;

    private static synchronized SsmClient ssm() {
        if (ssmClient == null) {
            ssmClient = SsmClient.builder()
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();
        }
        return ssmClient;
    }

    /** Uncached read, straight from SSM (region/credentials from the Lambda environment). */
    public static String fetchSsmParameterValueUncached(String ssmParameterKey) {
        return ssm().getParameter(r -> r.name(ssmParameterKey).withDecryption(true))
                .parameter().value();
    }

    public static void putSsmParameterValue(String ssmParameterKey, String value) {
        ssm().putParameter(r -> r.name(ssmParameterKey).value(value).overwrite(true));
    }

}
