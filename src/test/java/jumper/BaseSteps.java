package jumper;

import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.fail;

@Getter
@Setter
public class BaseSteps {

    private MockApiUpstreamServer mockUpstreamServer;
    private MockIrisServer mockIrisServer;

    protected Consumer<HttpHeaders> httpHeadersOfRequest;

    protected String authHeader;
    private String responseStatusCode;
    private WebTestClient webTestClient;
    private WebTestClient.ResponseSpec requestExchange;

    @And("API provider set to respond with a {int} status code")
    public void apiProviderWillRespondWithAStatusCode(int statusCode) {
        responseStatusCode = String.valueOf(statusCode);
    }

    @And("API consumer receives a {int} status code")
    public void apisConsumerReceivesAStatusCode(int arg0) {
        requestExchange.expectStatus().isEqualTo(arg0);
    }

    @And("several realm fields are contained in the header")
    public void addCommaSeparatedRealmListToHeader() {
        setHttpHeadersOfRequest(
                httpHeadersOfRequest.andThen(
                        httpHeaders -> {
                            httpHeaders.add(Constants.HEADER_REALM, "foo");
                            httpHeaders.add(Constants.HEADER_REALM, "huhuhu");
                            httpHeaders.add(Constants.HEADER_REALM, Constants.DEFAULT_REALM);
                        }
                )
        );
    }

    @And("IDP set to provide {word} token")
    public void apiProviderWillRespondWithAStatusCode(String tokenType) {
        switch (tokenType){
            case "internal": mockIrisServer.createExpectationInternalToken();
            break;
            case "external": mockIrisServer.createExpectationExternalToken();
            break;
            case "externalScoped": mockIrisServer.createExpectationExternalTokenScoped();
            break;
            case "externalHeader": mockIrisServer.createExpectationExternalTokenHeaderClient();
            break;
            case "externalHeaderScoped": mockIrisServer.createExpectationExternalTokenHeaderScopedClient();
            break;
            default: fail("expected tokenType not configured");
        }
    }

    @When("consumer calls the API")
    public void consumerCallsTheAPI() {
        mockUpstreamServer.callbackRequest();

        requestExchange = webTestClient.get().uri("/proxy/callback?statusCode=" + responseStatusCode).headers(httpHeadersOfRequest).exchange();
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
