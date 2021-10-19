package jumper;

import jumper.mocks.MockApiUpstreamServer;
import jumper.mocks.MockIrisServer;
import jumper.util.JumperConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationTest {

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
    public void testLastMileSecurity() {
        mockUpstreamServer.lastMileSecurityRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/lms").headers(JumperConfigurator.getJumperLmsHeaders()).exchange()
                .expectStatus().isOk();
    }

    @Test
    public void testEnhancedLastMileSecurity() {
        mockUpstreamServer.enhancedLastMileSecurityRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/elms").headers(JumperConfigurator.getJumperElmsHeaders()).exchange()
                .expectStatus().isOk();
    }

    @Test
    public void testGwMesh() {
        mockUpstreamServer.gwMeshRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/gw-mesh").headers(JumperConfigurator.getJumperMeshHeaders("stargate")).exchange()
                .expectStatus().isOk();
    }

    @Test
    public void testGwMeshInvalidAuth() {
        mockUpstreamServer.gwMeshRequest();
        mockIrisServer.createExpectationForInvalidAuth();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/gw-mesh").headers(JumperConfigurator.getJumperMeshHeaders("abc")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    public void testSpaceHeaders() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperSpaceHeaders("stargate")).exchange()
                .expectStatus().isOk();
    }

    @Test
    public void testSpaceHeadersWithNoGwMesh() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperLmsHeaders("stargate")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    public void testSpaceHeadersWithGwMeshNoSpace() {
        mockUpstreamServer.spaceRequest();
        mockIrisServer.gwMeshTokenRequest();

        WebTestClient clientReq = WebTestClient.bindToApplicationContext(this.context)
                .build();
        clientReq.get().uri("/proxy/space").headers(JumperConfigurator.getJumperMeshHeaders("stargate")).exchange()
                .expectStatus().is4xxClientError();
    }

}
