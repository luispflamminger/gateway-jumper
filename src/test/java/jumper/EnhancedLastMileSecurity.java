package jumper;

import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import jumper.util.JumperConfigurator;
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
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_ZONE, "aws")
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);
    }
}
