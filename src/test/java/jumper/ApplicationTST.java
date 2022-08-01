package jumper;

import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import jumper.util.JumperConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

//@RunWith(SpringRunner.class)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled
public class ApplicationTST {

    @Autowired
    private ApplicationContext context;

    MockApiUpstreamServer mockUpstreamServer;
    MockIrisServer mockIrisServer;

    @Before
    public void setup() {
        mockUpstreamServer = new MockApiUpstreamServer();
        mockUpstreamServer.startServer();

        mockIrisServer = new MockIrisServer();
        mockIrisServer.startServer();
    }

    @After
    public void tearDown() {
        mockUpstreamServer.stopServer();
        mockIrisServer.stopServer();
    }

    @Test
    @Disabled
    public void testSample() {
        mockUpstreamServer.callbackRequest();

        WebTestClient testClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:1080")
                .build();

        testClient.get().uri("/callback").exchange().expectStatus().isOk();
        testClient.get().uri("/callback").exchange()
                .expectHeader().doesNotExist(HttpHeaders.AUTHORIZATION)
                .expectStatus().isOk();

    }

    @Test
    @Disabled
    public void testLastMileSecurity() {
        //mockUpstreamServer.lastMileSecurityRequest();
        mockUpstreamServer.callbackRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/callback?statusCode=200").headers(JumperConfigurator.getJumperLmsHeaders()).exchange()
                .expectHeader().valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_LASTMILE_SECURITY_TOKEN, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
                .expectHeader().valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, "https://aws.local.de")
                .expectHeader().valueMatches(Constants.HEADER_X_ORIGIN_ZONE, "aws")
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
                .expectHeader().valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS)
                .expectStatus().isOk();

    }

    @Test
    @Disabled
    public void testEnhancedLastMileSecurity() {
        mockUpstreamServer.enhancedLastMileSecurityRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/elms").headers(JumperConfigurator.getJumperElmsHeaders()).exchange()
                .expectStatus().isOk();
    }

    @Test
    @Disabled
    public void testGwMesh() {
        mockUpstreamServer.gwMeshRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/gw-mesh").headers(JumperConfigurator.getJumperMeshHeaders("stargate")).exchange()
                .expectStatus().isOk();
    }

    @Test
    @Disabled
    public void testGwMeshInvalidAuth() {
        mockUpstreamServer.gwMeshRequest();
        mockIrisServer.createExpectationForInvalidAuth();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/gw-mesh").headers(JumperConfigurator.getJumperMeshHeaders("abc")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @Disabled
    public void testSpaceHeaders() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperSpaceHeaders("stargate")).exchange()
                .expectStatus().isOk();
    }

    @Test
    @Disabled
    public void testSpaceHeadersWithNoGwMesh() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperLmsHeaders("stargate")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @Disabled
    public void testSpaceHeadersWithGwMeshNoSpace() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperMeshHeaders("stargate")).exchange()
                .expectStatus().is4xxClientError();
    }

}
