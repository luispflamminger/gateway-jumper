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
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

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
        mockUpstreamServer.lastMileSecurityRequest(null);
    }

    @And("API Provider will respond with a {int} status code")
    public void apiProviderWillRespondWithAStatusCode(int arg0) {
        mockUpstreamServer.setResponse(arg0);
    }

    @When("consumer calls the API")
    public void consumerCallsTheAPI() {
        clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        responseSpec = clientReq.get().uri("/proxy/lms").headers(JumperConfigurator.getJumperLmsHeaders()).exchange();
        //.expectStatus().isOk();
    }

    @Then("API Provider receives AccessToken and GatewayToken")
    public void apiProviderReceivesAccessTokenAndGatewayToken() {

    }

    @And("API consumer receives a {int} status code")
    public void apiConsumerReceivesAStatusCode(int arg0) {
        responseSpec.expectStatus().isEqualTo(arg0);
    }


}
