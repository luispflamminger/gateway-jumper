package jumper.util;

import jumper.Constants;
import jumper.util.AccessToken;
import org.springframework.http.HttpHeaders;

import java.util.function.Consumer;

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

    private static String getConsumerAccessToken() {
        AccessToken consumerAccessToken = AccessToken.builder()
                .env("local")
                .clientId("eni--local-team--local-app")
                .originZone("aws")
                .originStargate("https://aws.local.de")
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
        return meshToken.getGwMeshToken();
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
}
