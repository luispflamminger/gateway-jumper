package jumper.mocks;

import lombok.Getter;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockHorizonServer {

    private ClientAndServer mockServer;

    @Getter
    private final int irisLocalPort = 1082;

    private final String irisLocalHost = "localhost";

    public void startServer() {
        mockServer = startClientAndServer(irisLocalPort);
    }

    public void stopServer() {
        mockServer.stop();
    }

    public void publishEventEndpoint() {

        List<Header> headersList = getHeaderList("69");

        new MockServerClient(irisLocalHost, irisLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withMethod("POST")
                                .withPath("/horizon/v1/events")
                                .withBody("my event"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(201)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "no-store"))
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    private List<Header> getHeaderList(String contentLength) {
        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/1.0.28"));
        headersList.add(new Header(HttpHeaders.HOST, irisLocalHost+":"+irisLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, contentLength));
        headersList.add(new Header(HttpHeaders.ACCEPT_ENCODING, "gzip"));
        headersList.add(new Header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8"));
        return headersList;
    }
}
