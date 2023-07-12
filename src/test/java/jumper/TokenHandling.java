package jumper;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import jumper.util.JumperConfigurator;
import jumper.utilities.OauthTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static jumper.util.Config.*;
import static jumper.util.JumperConfigUtil.*;
import static jumper.util.JumperConfigurator.getConsumerAccessTokenWithAud;
import static org.junit.jupiter.api.Assertions.*;

@RequiredArgsConstructor
public class TokenHandling{
    private final BaseSteps baseSteps;

    @Autowired
    WebTestClient webTestClient;

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    MockApiUpstreamServer mockUpstreamServer;
    MockIrisServer mockIrisServer;

    @Before("@default")
    public void beforeScenario() {
        mockUpstreamServer = new MockApiUpstreamServer();
        mockUpstreamServer.startServer();
        this.baseSteps.setMockUpstreamServer(mockUpstreamServer);

        mockIrisServer = new MockIrisServer();
        mockIrisServer.startServer();
        this.baseSteps.setMockIrisServer(mockIrisServer);

        this.baseSteps.setWebTestClient(webTestClient);

        this.baseSteps.setId(UUID.randomUUID().toString());
    }

    @After("@default")
    public void afterScenario() {
        mockUpstreamServer.stopServer();
        mockIrisServer.stopServer();
    }

    @Given("ProxyRoute headers are set")
    public void proxyRouteHeadersSet() {
        baseSteps.authHeader = JumperConfigurator.getConsumerAccessToken();
        baseSteps.setHttpHeadersOfRequest(JumperConfigurator.getProxyRouteHeaders(baseSteps));
    }

    @Given("RealRoute headers are set")
    public void realRouteHeadersSet() {
        baseSteps.setHttpHeadersOfRequest(JumperConfigurator.getRealRouteHeaders(JumperConfigurator.getConsumerAccessToken()));
    }

    @And("pub sub contained in the header")
    public void addPubSubInfoToHeader() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.add(Constants.HEADER_X_PUBSUB_PUBLISHER_ID, PUBSUB_PUBLISHER);
                            httpHeaders.add(Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID, PUBSUB_SUBSCRIBER);
                        }
                )
        );
    }

    @And("jumperConfig with scopes set")
    public void setJumperConfigScopes() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcSecurity());
                        }
                )
        );
    }

    @And("jumperConfig oauth {string} set")
    public void setJumperConfigOauth(String jc_case) {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            switch (jc_case) {
                                case "grant_type password":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauthGrantTypePassword(baseSteps.getId()));
                                    break;
                                case "grant_type password only":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauthGrantTypePasswordOnly(baseSteps.getId()));
                                    break;
                                case "grant_type client_credentials":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauthGrantType(baseSteps.getId()));
                                    break;
                                case "scoped":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauthWithScope(baseSteps.getId()));
                                    break;
                                default:
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauth(baseSteps.getId()));
                            }
                        }
                )
        );
    }

    @And("jumperConfig basic auth {string} set")
    public void setJumperConfigBasicAuth(String jc_case) {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            switch (jc_case) {
                                case "consumer key only":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthConsumer(baseSteps.getId()));
                                    break;
                                case "provider key only":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthProvider(baseSteps.getId()));
                                    break;
                                case "consumer and provider":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthConsumerAndProvider(baseSteps.getId()));
                                    break;
                                case "other consumer present":
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthOtherConsumer(baseSteps.getId()));
                                    break;
                                default:
                                    httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauth(baseSteps.getId()));
                            }
                        }
                )
        );
    }
    @And("oauth tokenEndpoint set")
    public void setOauthTokenEnpoint() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.set(Constants.HEADER_TOKEN_ENDPOINT, "http://localhost:1081/external");
                        }
                )
        );
    }

    @And("spacegate oauth headers set")
    public void setSpacegateOauthHeaders() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_ID, addIdSuffix(CONSUMER_EXTERNAL_HEADER, baseSteps.getId()));
                            httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_SECRET, "secret");
                        }
                )
        );
    }

    @And("spacegate oauth scoped headers set")
    public void setSpacegateOauthScopedHeaders() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_ID, addIdSuffix(CONSUMER_EXTERNAL_HEADER, baseSteps.getId()));
                            httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_SECRET, "secret");
                            httpHeaders.set(Constants.HEADER_X_SPACEGATE_SCOPE, OAUTH_SCOPE_HEADER);
                        }
                )
        );
    }

    @And("authorization token with aud set")
    public void setAuthorizationWithAud() {
        baseSteps.setHttpHeadersOfRequest(
                baseSteps.httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.setBearerAuth(getConsumerAccessTokenWithAud());
                        }
                )
        );
    }

    @Then("API Provider receives default bearer authorization headers")
    public void apiProvidersReceivesDefaultTokenHeaders(){
        this.baseSteps.getRequestExchange()
                .expectHeader().valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern());
        apiProvidersReceivesDefaultHeaders();
    }

    @Then("API Provider receives default basic authorization headers")
    public void apiProvidersReceivesDefaultBasicAuthHeaders(){
        this.baseSteps.getRequestExchange()
                .expectHeader().valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Basic\\s\\w+").pattern());
        apiProvidersReceivesDefaultHeaders();
    }

    public void apiProvidersReceivesDefaultHeaders(){
        this.baseSteps.getRequestExchange()
                .expectHeader().valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, ORIGIN_STARGATE)
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_ZONE, ORIGIN_ZONE)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_HOST, "zone.local.de")
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS)
                ;
    }

    @Then("API Provider receives authorization {word}")
    public void apiProviderReceivesToken(String tokenType) {
        if (tokenType.equalsIgnoreCase("OneToken")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkOneToken);
        }
        else if (tokenType.equalsIgnoreCase("OneTokenWithPubSub")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkPubSub);
        }
        else if (tokenType.equalsIgnoreCase("OneTokenWithScopes")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkScopes);
        }
        else if (tokenType.equalsIgnoreCase("OneTokenWithAud")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkAud);
        }
        else if (tokenType.equalsIgnoreCase("MeshToken")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkMeshToken)
                    .expectHeader().valueMatches(Constants.HEADER_CONSUMER_TOKEN, "Bearer " + baseSteps.authHeader);
        }
        else if (tokenType.equalsIgnoreCase("ExternalConfigured")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkExternalConfigured);
        }
        else if (tokenType.equalsIgnoreCase("ExternalHeader")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkExternalHeader);
        }
        else if (tokenType.equalsIgnoreCase("BasicAuthConsumer")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkBasicAuthConsumer);
        }
        else if (tokenType.equalsIgnoreCase("BasicAuthProvider")){
            this.baseSteps.getRequestExchange()
                    .expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkBasicAuthProvider);
        }
        else {
            fail("unknown authorization received");
        }
    }

    private void checkOneToken(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals("Bearer", claimsFromToken.getBody().get( "typ", String.class));
        assertEquals(CONSUMER, claimsFromToken.getBody().get( "clientId", String.class));
        assertEquals("stargate", claimsFromToken.getBody().get( "azp", String.class));
        assertEquals(ENVIRONMENT, claimsFromToken.getBody().get( "env", String.class));
        assertEquals("GET", claimsFromToken.getBody().get( "operation", String.class));
        assertEquals(BASE_PATH + CALLBACK_SUFFIX, claimsFromToken.getBody().get( "requestPath", String.class));
        assertEquals(ORIGIN_ZONE, claimsFromToken.getBody().get( "originZone", String.class));
        assertEquals(ORIGIN_STARGATE, claimsFromToken.getBody().get( "originStargate", String.class));
        assertEquals(localIssuerUrl + "/" + Constants.DEFAULT_REALM, claimsFromToken.getBody().getIssuer());
        assertNotNull(claimsFromToken.getBody().getExpiration());
        assertNotNull(claimsFromToken.getBody().getIssuedAt());
    }

    private void checkPubSub(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals(PUBSUB_PUBLISHER, claimsFromToken.getBody().get("publisherId", String.class));
        assertEquals(PUBSUB_SUBSCRIBER, claimsFromToken.getBody().get("subscriberId", String.class));
        assertEquals(PUBSUB_SUBSCRIBER, claimsFromToken.getBody().get("aud", String.class));
    }

    private void checkScopes(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals(SCOPES, claimsFromToken.getBody().get("scope", String.class));
    }

    private void checkAud(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals("testAudience", claimsFromToken.getBody().get("aud", String.class));
    }


    private void checkMeshToken(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals("Bearer", claimsFromToken.getBody().get( "typ", String.class));
        assertEquals(CONSUMER_GATEWAY, claimsFromToken.getBody().get( "clientId", String.class));
        assertEquals(CONSUMER_GATEWAY, claimsFromToken.getBody().get( "azp", String.class));
        assertEquals(ENVIRONMENT_REMOTE, claimsFromToken.getBody().get( "env", String.class));
        assertEquals(ORIGIN_ZONE_REMOTE, claimsFromToken.getBody().get( "originZone", String.class));
        assertEquals(ORIGIN_STARGATE_REMOTE, claimsFromToken.getBody().get( "originStargate", String.class));
        assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
        assertNotNull(claimsFromToken.getBody().getExpiration());
        assertNotNull(claimsFromToken.getBody().getIssuedAt());
    }

    private void checkExternalConfigured(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals(CONSUMER_EXTERNAL_CONFIGURED, claimsFromToken.getBody().get("clientId", String.class));
        assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
    }

    private void checkExternalHeader(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

        assertEquals(CONSUMER_EXTERNAL_HEADER, claimsFromToken.getBody().get("clientId", String.class));
        assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
    }

    private void checkBasicAuthConsumer(String basicAuthEncoded) {
        String basicAuthDecoded = new String(Base64.getDecoder().decode( basicAuthEncoded.replaceFirst("Basic ", "").getBytes()));
        String[] basicAuthSplitted = basicAuthDecoded.split(":");
        assertEquals(addIdSuffix(CONSUMER, this.baseSteps.getId()), basicAuthSplitted[0]);
        assertEquals("password", basicAuthSplitted[1]);
    }

    private void checkBasicAuthProvider(String basicAuthEncoded) {
        String basicAuthDecoded = new String(Base64.getDecoder().decode( basicAuthEncoded.replaceFirst("Basic ", "").getBytes()));
        String[] basicAuthSplitted = basicAuthDecoded.split(":");
        assertEquals(addIdSuffix(CONSUMER_GATEWAY, this.baseSteps.getId()), basicAuthSplitted[0]);
        assertEquals("geheim", basicAuthSplitted[1]);
    }

}
