package jumper.mocks;

import jumper.Constants;
import jumper.util.JumperConfigurator;
import lombok.Getter;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.StringBody.exact;

public class MockApiUpstreamServer {

    @Getter
    private final int upstreamLocalPort = 1080;

    private final String upstreamLocalHost = "localhost";

    private ClientAndServer mockServer;

    public void startServer() {
        mockServer = startClientAndServer(upstreamLocalPort);
    }

    public void stopServer() {
        mockServer.stop();
    }

    public void lastMileSecurityRequest() {

        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header("WebTestClient-Request-Id", "1"));
        headersList.add(new Header(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_LASTMILE_SECURITY_TOKEN, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de"));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_ZONE, "aws"));
        headersList.add(new Header(Constants.HEADER_X_FORWARDED_PORT, "80"));
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/0.9.8.RELEASE"));
        headersList.add(new Header(HttpHeaders.HOST, upstreamLocalHost + ":" + upstreamLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SAMPLED, "1"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, "0"));

        new MockServerClient(upstreamLocalHost, upstreamLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withMethod("GET")
                                .withPath("/lms"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'lastMileSecurity' }")
                                .withDelay(TimeUnit.SECONDS, 1)

                );
    }

    public void createExpectationForInvalidAuth() {
        new MockServerClient(upstreamLocalHost, upstreamLocalPort)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/validate")
                                .withHeader("\"Content-type\", \"application/json\"")
                                .withBody(exact("{username: 'foo', password: 'bar'}")),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(401)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'incorrect username and password combination' }")
                                .withDelay(TimeUnit.SECONDS, 1)
                );
    }

    public void gwMeshRequest() {
        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header("WebTestClient-Request-Id", "1"));
        headersList.add(new Header(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de"));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_ZONE, "aws"));
        headersList.add(new Header(Constants.HEADER_X_FORWARDED_PORT, "80"));
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/0.9.8.RELEASE"));
        headersList.add(new Header(HttpHeaders.HOST, upstreamLocalHost + ":" + upstreamLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SAMPLED, "1"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, "0"));

        new MockServerClient(upstreamLocalHost, upstreamLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withHeader(not(Constants.HEADER_ACCESS_TOKEN_FORWARDING))
                                .withMethod("GET")
                                .withPath("/gw-mesh"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'gwMesh' }")
                                .withDelay(TimeUnit.SECONDS, 1)

                );
    }

    public void spaceRequest() {
        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header("WebTestClient-Request-Id", "1"));
        headersList.add(new Header(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_STARGATE, "https://space.local.de"));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_ZONE, "space"));
        headersList.add(new Header(Constants.HEADER_X_SPACEGATE_TOKEN, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_FORWARDED_PORT, "80"));
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/0.9.8.RELEASE"));
        headersList.add(new Header(HttpHeaders.HOST, upstreamLocalHost + ":" + upstreamLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SAMPLED, "1"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, "0"));

        new MockServerClient(upstreamLocalHost, upstreamLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withHeader(not(Constants.HEADER_ACCESS_TOKEN_FORWARDING))
                                .withMethod("GET")
                                .withPath("/space"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'space' }")
                                .withDelay(TimeUnit.SECONDS, 1)

                );
    }

    public void enhancedLastMileSecurityRequest() {
        List<Header> headersList = new ArrayList<>();
        headersList.add(new Header("WebTestClient-Request-Id", "1"));
        headersList.add(new Header(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de"));
        headersList.add(new Header(Constants.HEADER_X_ORIGIN_ZONE, "aws"));
        headersList.add(new Header(Constants.HEADER_X_FORWARDED_PORT, "80"));
        headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/0.9.8.RELEASE"));
        headersList.add(new Header(HttpHeaders.HOST, upstreamLocalHost + ":" + upstreamLocalPort));
        headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
        headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern()));
        headersList.add(new Header(Constants.HEADER_X_B3_SAMPLED, "1"));
        headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, "0"));

        new MockServerClient(upstreamLocalHost, upstreamLocalPort)
                .when(
                        request()
                                .withHeaders(headersList)
                                .withHeader(not(Constants.HEADER_ACCESS_TOKEN_FORWARDING))
                                .withMethod("GET")
                                .withPath("/elms"),
                        exactly(1))
                .respond(
                        response()
                                .withStatusCode(200)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody("{ message: 'enhancedLastMileSecurity' }")
                                .withDelay(TimeUnit.SECONDS, 1)

                );
    }
}
