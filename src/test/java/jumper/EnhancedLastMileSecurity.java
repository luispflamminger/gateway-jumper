package jumper;

import static org.junit.Assert.assertEquals;

import io.cucumber.java.After;
import io.cucumber.java.Before;
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
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@AutoConfigureWebTestClient(timeout = "PT65S") // PT65S - PT = Period time, S = seconds
public class EnhancedLastMileSecurity {
    private final BaseSteps baseSteps;

    @Autowired
    WebTestClient webTestClient;

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    MockApiUpstreamServer mockUpstreamServer;
    MockIrisServer mockIrisServer;

    Consumer<HttpHeaders> httpHeadersOfRequest;

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

    @Given("EnhancedLastMileSecurity is activated")
    public void enhancedlastmilesecurityIsActivated() {
        httpHeadersOfRequest = JumperConfigurator.getJumperElmsHeaders();
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

        this.baseSteps.getRequestExchange().expectHeader().value(HttpHeaders.AUTHORIZATION, this::checkToken);
    }

    private void checkToken(String token) {
        Jwt<Header, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));
        assertEquals(localIssuerUrl + "/" + Constants.DEFAULT_REALM, claimsFromToken.getBody().get( "iss", String.class));
    }
}
