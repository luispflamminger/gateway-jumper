// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import jumper.model.config.HealthStatus;
import jumper.model.config.ZoneHealthMessage;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Disabled
class RedisZoneHealthStatusServiceTest {

  @Value("${jumper.zone.health.redis.channel}")
  private String channelKey;

  @Autowired private RedisTemplate<String, String> redisTemplate;

  @Autowired private ObjectMapper objectMapper;

  @SpyBean private RedisZoneHealthStatusService redisZoneHealthStatusService;

  @SpyBean private ZoneHealthCheckService zoneHealthCheckService;

  @BeforeEach
  void setUp() {
    Mockito.reset(zoneHealthCheckService);
    Mockito.reset(redisZoneHealthStatusService);
  }

  @Test
  @DisplayName(
      "Test if a zone is marked correctly after receiving message via redis with a unhealthy status message")
  void getZoneUnhealthyWithRedisPubSubListener() throws JsonProcessingException {
    // given
    String zoneToTest = "zoneToTest";
    ZoneHealthMessage message = new ZoneHealthMessage(zoneToTest, HealthStatus.UNHEALTHY);
    var messageString = objectMapper.writeValueAsString(message);

    // when
    redisTemplate.convertAndSend(channelKey, messageString);

    // then
    Mockito.verify(redisZoneHealthStatusService, Mockito.timeout(5000L).times(1))
        .onMessage(Mockito.any(), Mockito.any());
    Mockito.verify(zoneHealthCheckService, Mockito.timeout(5000L).times(1))
        .setZoneHealth(Mockito.anyString(), Mockito.anyBoolean());
    assertFalse(zoneHealthCheckService.getZoneHealth(zoneToTest));
  }

  static RedisServer redisServer;
  static int randomRedisPort;

  static {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      assertNotNull(serverSocket);
      assertTrue(serverSocket.getLocalPort() > 0);
      randomRedisPort = serverSocket.getLocalPort();
      redisServer = new RedisServer(randomRedisPort);
    } catch (IOException e) {
      fail("Port is not available");
    }
  }

  @BeforeAll
  static void startRedis() throws IOException {
    redisServer.start();
  }

  @AfterAll
  static void stopRedis() throws IOException {
    redisServer.stop();
  }

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.redis.port", () -> randomRedisPort);
  }
}
