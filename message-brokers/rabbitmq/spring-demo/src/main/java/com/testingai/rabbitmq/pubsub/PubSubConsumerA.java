package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PubSubConsumerA {

    @RabbitListener(queues = PubSubConfig.QUEUE_A, containerFactory = "pubSubContainerFactory")
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        try {
            FailureSimulator.maybeThrow("[PubSubConsumerA]");
            log.info("[PubSubConsumerA] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            long retryCount = xDeath == null ? 0L : (Long) xDeath.get(0).get("count");
            if (retryCount >= PubSubConfig.MAX_RETRIES) {
                log.error("[PubSubConsumerA] Max retries ({}) exceeded, discarding: {}", PubSubConfig.MAX_RETRIES, message);
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("[PubSubConsumerA] Failed (retry {}/{}), sending to retry queue: {}", retryCount + 1, PubSubConfig.MAX_RETRIES, e.getMessage());
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (IOException e) {
            throw e;
        }
    }
}
