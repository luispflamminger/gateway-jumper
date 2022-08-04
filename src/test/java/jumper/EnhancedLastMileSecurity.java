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
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static jumper.util.Config.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT65S") // PT65S - PT = Period time, S = seconds
public class EnhancedLastMileSecurity {
    private final BaseSteps baseSteps;

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    private ApplicationContext context;

    MockApiUpstreamServer mockUpstreamServer;
    MockIrisServer mockIrisServer;

    Consumer<HttpHeaders> httpHeadersOfRequest;
    String responseStatusCode;

    WebTestClient.ResponseSpec requestExchange;

    String jwtTokenResponse;

    @Before("@elms")
    public void beforeScenario() {
        mockUpstreamServer = new MockApiUpstreamServer();
        mockUpstreamServer.startServer();
        this.baseSteps.setMockUpstreamServer(mockUpstreamServer);

        mockIrisServer = new MockIrisServer();
        mockIrisServer.startServer();
        this.baseSteps.setMockIrisServer(mockIrisServer);

        this.baseSteps.setWebTestClient(webTestClient);
    }

    @After("@elms")
    public void afterScenario() {
        mockUpstreamServer.stopServer();
        mockIrisServer.stopServer();
    }

    public EnhancedLastMileSecurity(BaseSteps baseSteps) {
        this.baseSteps = baseSteps;
    }

    @Given("EnhancedLastMileSecurity is activated")
    public void enhancedlastmilesecurityIsActivated() {
        httpHeadersOfRequest = JumperConfigurator.getJumperElmsHeaders();
        baseSteps.setHttpHeadersOfRequest(httpHeadersOfRequest);
    }

    @And("JumperConfig security scope is added")
    public void addJcSecurity(){
        //todo just extend
        httpHeadersOfRequest = JumperConfigurator.getJumperElmsHeadersWithSecurity();
        baseSteps.setHttpHeadersOfRequest(httpHeadersOfRequest);
    }



    @Then("API Provider receives {word}")
    public void apiProviderReceivesMergedGatewayToken(String mergedGatewayToken) {
        if(!Objects.equals(mergedGatewayToken, "MergedGatewayToken")) {
            this.baseSteps.getRequestExchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
            return;
        }
        this.baseSteps.getRequestExchange()
                .expectHeader().valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, "https://zone.local.de")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_ZONE, "localZone")
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);

        this.baseSteps.getRequestExchange().expectHeader().value(HttpHeaders.AUTHORIZATION, s -> {
            jwtTokenResponse = s;
        });

        /*"sub": "4e1bd9f4-a4d6-4c92-a256-ba9ccea6564b",
  "clientId": "eni--local-team--local-app",
  "azp": "stargate",
  "originZone": "localZone",
  "scope": "scope1 scope2",
  "typ": "Bearer",
  "operation": "GET",
  "requestPath": "null/callback",
  "originStargate": "https://zone.local.de",
  "iss": "https://stargate-integration.test.dhei.telekom.de/auth/realms/default",
  "exp": 1659595531,
  "iat": 1659595231

         */

        String jwtToken = OauthTokenUtil.getTokenWithoutSignature(jwtTokenResponse);
        Jwt<Header, Claims> allClaimsFromConsumerToken = OauthTokenUtil.getAllClaimsFromConsumerToken(jwtToken);
        Assert.assertEquals(CONSUMER, allClaimsFromConsumerToken.getBody().get( "clientId", String.class));
        Assert.assertEquals("stargate", allClaimsFromConsumerToken.getBody().get( "azp", String.class));
        Assert.assertEquals(ORIGIN_ZONE, allClaimsFromConsumerToken.getBody().get( "originZone", String.class));
        Assert.assertEquals("Bearer", allClaimsFromConsumerToken.getBody().get( "typ", String.class));
        Assert.assertEquals("GET", allClaimsFromConsumerToken.getBody().get( "operation", String.class));
        //Assert.assertEquals("", allClaimsFromConsumerToken.getBody().get( "requestPath", String.class));
        Assert.assertEquals(ORIGIN_STARGATE, allClaimsFromConsumerToken.getBody().get( "originStargate", String.class));
    }

    @And("Authorization token contains scope claim")
    public void authorizationTokenContainsScopeClaim() {
        this.baseSteps.getRequestExchange().expectHeader().value(HttpHeaders.AUTHORIZATION, s -> {
                    jwtTokenResponse = s;
                });

        String jwtToken = OauthTokenUtil.getTokenWithoutSignature(jwtTokenResponse);
        Jwt<Header, Claims> allClaimsFromConsumerToken = OauthTokenUtil.getAllClaimsFromConsumerToken(jwtToken);
        Assert.assertEquals(SCOPES, allClaimsFromConsumerToken.getBody().get( "scope", String.class));
    }
}
