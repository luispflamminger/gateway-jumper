package jumper.mocks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.model.TokenInfo;
import jumper.util.AccessToken;
import lombok.Getter;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static jumper.util.Config.*;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockIrisServer {

    private ClientAndServer mockServer;

    @Getter
    private final int irisLocalPort = 1081;

    private final String irisLocalHost = "localhost";

    public void startServer() {
        mockServer = startClientAndServer(irisLocalPort);
    }

    public void stopServer() {
        mockServer.stop();
    }

    public void createExpectationInternalToken() {

        String tokenInfoJson = getTokenInfoJson(getMeshToken());
        List<Header> headersList = getHeaderList("69");

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withMethod("POST")
                                .withPath("/auth/realms/default/protocol/openid-connect/token")
                                .withBody("client_id=stargate&client_secret=secret&grant_type=client_credentials"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withBody(tokenInfoJson)
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void createExpectationExternalToken() {

        String tokenInfoJson = getTokenInfoJson(getExternalToken(CONSUMER_EXTERNAL_CONFIGURED));

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/external")
                                .withBody("client_id=external_configured&client_secret=secret&grant_type=client_credentials"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withBody(tokenInfoJson)
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void createExpectationExternalTokenScoped() {

        String tokenInfoJson = getTokenInfoJson(getExternalToken(CONSUMER_EXTERNAL_CONFIGURED));

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/external")
                                .withBody("client_id=external_configured&client_secret=secret&grant_type=client_credentials&scope=scope_configured"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withBody(tokenInfoJson)
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void createExpectationExternalTokenHeaderClient() {

        String tokenInfoJson = getTokenInfoJson(getExternalToken(CONSUMER_EXTERNAL_HEADER));

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/external")
                                .withBody("client_id=external_header&client_secret=secret&grant_type=client_credentials"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withBody(tokenInfoJson)
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void createExpectationExternalTokenHeaderScopedClient() {

        String tokenInfoJson = getTokenInfoJson(getExternalToken(CONSUMER_EXTERNAL_HEADER));

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/external")
                                .withBody("client_id=external_header&client_secret=secret&grant_type=client_credentials&scope=scope_header"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withBody(tokenInfoJson)
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void createExpectationForInvalidAuth() {
        List<Header> headersList = getHeaderList("64");

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withMethod("POST")
                                .withPath("/auth/realms/default/protocol/openid-connect/token")
                                .withBody("client_id=abc&client_secret=secret&grant_type=client_credentials"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(401)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'incorrect clientId and secret combination' }")
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    private List<Header> getHeaderList(String contentLength) {
        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/1.0.28"));
        headersList.add(new Header(HttpHeaders.HOST, irisLocalHost+":"+irisLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, contentLength));
        headersList.add(new Header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8"));
        return headersList;
    }

    private String getTokenInfoJson(String token) {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setAccessToken(token);
        tokenInfo.setRefreshToken("asd");
        tokenInfo.setExpiresIn(300);
        tokenInfo.setRefreshExpiresIn(1800);
        tokenInfo.setTokenType("bearer");
        tokenInfo.setNotBeforePolicy(0);
        tokenInfo.setSessionState("69fc4e8-77e9-45f9-93e4-646a34f802cc");
        tokenInfo.setScope("profile email");

        ObjectMapper mapper = new ObjectMapper();
        String tokenInfoJson = null;
        try {
            tokenInfoJson = mapper.writeValueAsString(tokenInfo);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return tokenInfoJson;
    }

    private String getMeshToken() {
        AccessToken meshToken = AccessToken.builder()
                .env(ENVIRONMENT_REMOTE)
                .clientId(CONSUMER_GATEWAY)
                .originZone(ORIGIN_ZONE_REMOTE)
                .originStargate(ORIGIN_STARGATE_REMOTE)
                .build();
        return meshToken.getIdpToken();
    }

    private String getExternalToken(String client) {
        AccessToken externalToken = AccessToken.builder()
                .env(ENVIRONMENT_REMOTE)
                .clientId(client)
                .originZone(ORIGIN_ZONE_REMOTE)
                .originStargate(ORIGIN_STARGATE_REMOTE)
                .build();
        return externalToken.getIdpToken();
    }

}
