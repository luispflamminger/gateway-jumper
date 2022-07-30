package jumper;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.CucumberOptions;
import io.cucumber.spring.CucumberContextConfiguration;
import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import jumper.util.JumperConfigurator;
import org.junit.runner.RunWith;
import org.mockserver.client.server.ForwardChainExpectation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.function.Consumer;
import java.util.regex.Pattern;

//@RunWith(Cucumber.class)
@RunWith(SpringRunner.class)
@CucumberOptions(features = "src/test/resources/features")
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JumperTest {

    @Autowired
    private ApplicationContext context;

    MockApiUpstreamServer mockUpstreamServer;
    MockIrisServer mockIrisServer;
    private WebTestClient clientReq;
    private WebTestClient.ResponseSpec responseSpec;
    private ForwardChainExpectation forwardChainExpectation;

    Consumer<HttpHeaders> httpHeadersOfRequest;
    String responseStatusCode;

    WebTestClient.ResponseSpec requestExchange;

    @Before
    public void beforeStep() {
        mockUpstreamServer = new MockApiUpstreamServer();
        mockUpstreamServer.startServer();

        mockIrisServer = new MockIrisServer();
        mockIrisServer.startServer();
    }

    @After
    public void afterStep() {
        mockUpstreamServer.stopServer();
        mockIrisServer.stopServer();
    }

    @Given("lastMileSecurity is activated")
    public void lastMileSecurityIsActivated() {
        httpHeadersOfRequest = JumperConfigurator.getJumperLmsHeaders();
    }

    @And("API Provider will respond with a {int} status code")
    public void apiProviderWillRespondWithAStatusCode(int statusCode) {
        responseStatusCode = String.valueOf(statusCode);
    }

    @When("consumer calls the API")
    public void consumerCallsTheAPI() {
        mockUpstreamServer.callbackRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        requestExchange = clientReq.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
        /*
        clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        responseSpec = clientReq.get().uri("/proxy/lms").headers(httpHeadersOfRequest).exchange();
        .expectStatus().isOk();
         */
    }

    @Then("API Provider receives AccessToken and GatewayToken")
    public void apiProviderReceivesAccessTokenAndGatewayToken() {
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


}
