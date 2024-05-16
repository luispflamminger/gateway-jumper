// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.config.RedisConfig;
import jumper.model.config.HealthStatus;
import jumper.model.config.ZoneHealthMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnBean(RedisConfig.class)
@Component
public class RedisZoneHealthStatusService implements MessageListener {

  private final ObjectMapper objectMapper;
  private final ZoneHealthCheckService zoneHealthCheckService;
  private final RedisMessageListenerContainer redisMessageListenerContainer;

  private final String channelKey;

  public RedisZoneHealthStatusService(
      ObjectMapper objectMapper,
      ZoneHealthCheckService zoneHealthCheckService,
      RedisMessageListenerContainer redisMessageListenerContainer,
      @Value("${jumper.zone.health.redis.channel}") String channelKey) {
    this.objectMapper = objectMapper;
    this.zoneHealthCheckService = zoneHealthCheckService;
    this.redisMessageListenerContainer = redisMessageListenerContainer;
    this.channelKey = channelKey;

    this.lazyInitializeRedisMessageListenerContainer();
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      ZoneHealthMessage zoneHealthMessage =
          objectMapper.readValue(message.toString(), ZoneHealthMessage.class);
      log.debug("Received message {}", zoneHealthMessage);
      zoneHealthCheckService.setZoneHealth(
          zoneHealthMessage.getZone(), zoneHealthMessage.getStatus() == HealthStatus.HEALTHY);
    } catch (JsonProcessingException e) {
      log.error("Error deserializing message", e);
    } catch (Exception e) {
      log.error("Error processing message", e);
    }
  }

  @Async
  void lazyInitializeRedisMessageListenerContainer() {
    var template =
        new RetryTemplateBuilder().maxAttempts(Integer.MAX_VALUE).fixedBackoff(5000).build();
    template.execute(
        context -> {
          try {
            if (redisMessageListenerContainer.getConnectionFactory() == null) {
              log.debug("Redis connection factory not available, skipping initialization");
              return null;
            }

            var connection = redisMessageListenerContainer.getConnectionFactory().getConnection();
            if (connection.isSubscribed()) {
              log.debug("Redis connection already subscribed, skipping initialization");
              return null;
            }
          } catch (Exception e) {
            log.error("Connection failure occurred. Restarting subscription task after 5000 ms");
            throw e;
          }
          redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(channelKey));
          log.debug("Listeners registered successfully after {} retries.", context.getRetryCount());
          return null;
        });
  }
}
