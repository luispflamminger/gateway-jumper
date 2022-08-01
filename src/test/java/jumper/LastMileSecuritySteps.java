package jumper;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
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

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT65S") // PT65S - PT = Period time, S = seconds
public class LastMileSecuritySteps {

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

    public LastMileSecuritySteps(BaseSteps baseSteps) {
        this.baseSteps = baseSteps;
    }

    @Before("@lms")
    public void beforeScenario() {
        mockUpstreamServer = new MockApiUpstreamServer();
        mockUpstreamServer.startServer();

        mockIrisServer = new MockIrisServer();
        mockIrisServer.startServer();
    }

    @After("@lms")
    public void afterScenario() {
        mockUpstreamServer.stopServer();
        mockIrisServer.stopServer();
    }

    @Given("lastMileSecurity is activated")
    public void lastMileSecurityIsActivated() {
        httpHeadersOfRequest = JumperConfigurator.getJumperLmsHeaders();
    }

    @When("consumer calls the API")
    public void consumerCallsTheAPI() {
        mockUpstreamServer.callbackRequest();

        responseStatusCode = this.baseSteps.getResponseStatusCode();

        requestExchange = webTestClient.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
    }

    @Then("API Provider receives {word} and {word}")
    public void apiProviderReceivesAccessTokenAndGatewayToken(String at, String gt) {
        if(!Objects.equals(at, "AccessToken") || !Objects.equals(gt, "GatewayToken")) {
            requestExchange.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
            return;
        }
        requestExchange
                .expectHeader().valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_LASTMILE_SECURITY_TOKEN, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_ZONE, "aws")
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);
    }

    @And("API consumer receives a {int} status code")
    public void apiConsumerReceivesAStatusCode(int arg0) {
        requestExchange.expectStatus().isEqualTo(arg0);
    }

    @When("consumer calls the API and runs into timeout")
    public void consumerCallsTheAPIAndProviderRunsIntoTimeout() {
        mockUpstreamServer.callbackRequestWithTimeout();

        requestExchange = webTestClient.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
    }

    @When("consumer calls the API and connection is dropped")
    public void consumerCallsTheAPIAndConnectionIsDropped() {
        mockUpstreamServer.callbackRequestWithDropConnection();

        requestExchange = webTestClient.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
    }
}
