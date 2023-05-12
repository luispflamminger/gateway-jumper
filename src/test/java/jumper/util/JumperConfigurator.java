package jumper.util;

import jumper.Constants;
import org.springframework.http.HttpHeaders;

import java.util.function.Consumer;

import static jumper.util.Config.*;
import static jumper.util.JumperConfigUtil.getJcSecurity;

public class JumperConfigurator {

    public static Consumer<HttpHeaders> getJumperLmsHeaders(String consumerToken) {
        return httpHeaders -> {
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.setBearerAuth(consumerToken);
        };
    }

    public static Consumer<HttpHeaders> getJumperLmsHeaders() {
        return getJumperLmsHeaders(getConsumerAccessToken());
    }

    public static String getConsumerAccessToken() {
        AccessToken consumerAccessToken = AccessToken.builder()
                .env("local")
                .clientId("eni--local-team--local-app")
                .originZone("localZone")
                .originStargate("https://zone.local.de")
                .build();
        return consumerAccessToken.getConsumerAccessToken();
    }

    public static String getConsumerAccessTokenWithAud() {
        AccessToken consumerAccessToken = AccessToken.builder()
                .env("local")
                .clientId("eni--local-team--local-app")
                .originZone("localZone")
                .originStargate("https://zone.local.de")
                .audience("testAudience")
                .build();
        return consumerAccessToken.getConsumerAccessToken();
    }

    private static String getSpaceConsumerAccessToken() {
        AccessToken consumerAccessToken = AccessToken.builder()
                .env("local")
                .clientId("eni--local-team--local-app")
                .originZone("space")
                .originStargate("https://space.local.de")
                .build();
        return consumerAccessToken.getConsumerAccessToken();
    }

    public static Consumer<HttpHeaders> getJumperMeshHeaders(String clientId) {
        return httpHeaders -> {
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.set(Constants.HEADER_ISSUER, "http://localhost:1081/auth/realms/default");
            httpHeaders.set(Constants.HEADER_CLIENT_ID, clientId);
            httpHeaders.set(Constants.HEADER_CLIENT_SECRET, "secret");
            httpHeaders.setBearerAuth(getConsumerAccessToken());
        };
    }

    private static String getMeshToken() {
        AccessToken meshToken = AccessToken.builder()
                .env("local")
                .clientId("stargate")
                .originZone("aws")
                .originStargate("https://aws.local.de")
                .build();
        return meshToken.getIdpToken();
    }

    public static Consumer<HttpHeaders> getJumperElmsHeaders() {
        return httpHeaders -> {
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.setBearerAuth(getConsumerAccessToken());
            httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
        };
    }

    public static Consumer<HttpHeaders> getJumperSpaceHeaders(String clientId) {
        return httpHeaders -> {
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.set(Constants.HEADER_ISSUER, "http://localhost:1081/auth/realms/default");
            httpHeaders.set(Constants.HEADER_CLIENT_ID, clientId);
            httpHeaders.set(Constants.HEADER_CLIENT_SECRET, "secret");
            httpHeaders.setBearerAuth(getSpaceConsumerAccessToken());
        };
    }

    public static Consumer<HttpHeaders> getJumperElmsHeadersWithSecurity() {
        return httpHeaders -> {
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcSecurity());
            httpHeaders.setBearerAuth(getConsumerAccessToken());
            httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
        };
    }

    public static Consumer<HttpHeaders> getProxyRouteHeaders(String authorization){
        return httpHeaders -> {
            httpHeaders.setBearerAuth(authorization);
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.set(Constants.HEADER_ISSUER, "http://localhost:1081/auth/realms/default");
            httpHeaders.set(Constants.HEADER_CLIENT_ID, "stargate");
            httpHeaders.set(Constants.HEADER_CLIENT_SECRET, "secret");
            httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
        };
    }

    public static Consumer<HttpHeaders> getRealRouteHeaders(String authorization){
        return httpHeaders -> {
            httpHeaders.setBearerAuth(authorization);
            httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
            httpHeaders.set(Constants.HEADER_API_BASE_PATH, BASE_PATH);
            httpHeaders.set(Constants.HEADER_ENVIRONMENT, ENVIRONMENT);
            httpHeaders.set(Constants.HEADER_REALM, REALM);
            httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
            httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
        };
    }
}
