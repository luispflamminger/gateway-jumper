package jumper;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.function.Consumer;

@Getter
@Setter
public class BaseSteps {

    private MockApiUpstreamServer mockUpstreamServer;
    private MockIrisServer mockIrisServer;

    private Consumer<HttpHeaders> httpHeadersOfRequest;

    private String responseStatusCode;
    private WebTestClient webTestClient;
    private WebTestClient.ResponseSpec requestExchange;

    @And("API Provider will respond with a {int} status code")
    public void apiProviderWillRespondWithAStatusCode(int statusCode) {
        responseStatusCode = String.valueOf(statusCode);
    }

    @And("APIs consumer receives a {int} status code")
    public void apisConsumerReceivesAStatusCode(int arg0) {
        requestExchange.expectStatus().isEqualTo(arg0);
    }

    @When("consumer calls the APIs")
    public void consumerCallsTheAPIs() {
        mockUpstreamServer.callbackRequest();

        requestExchange = webTestClient.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
    }
}
