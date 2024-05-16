// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.util.AssertionErrors.fail;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

public class AbstractIntegrationTest {

  static RedisServer redisServer;
  static int port;

  static {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      assertThat(serverSocket).isNotNull();
      port = serverSocket.getLocalPort();
      redisServer = new RedisServer(port);
      assertThat(port).isGreaterThan(0);
      redisServer.start();
    } catch (IOException e) {
      System.out.println(e);
      fail("Could not start embedded Redis server");
    }
  }

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.redis.port", () -> port);
  }

  @AfterAll
  static void stopRedis() throws IOException {
    redisServer.stop();
  }
}
