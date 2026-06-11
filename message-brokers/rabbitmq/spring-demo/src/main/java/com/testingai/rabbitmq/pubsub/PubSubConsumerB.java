package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class PubSubConsumerB {

    @RabbitListener(queues = PubSubConfig.QUEUE_B, containerFactory = "pubSubContainerFactory")
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[PubSubConsumerB] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[PubSubConsumerB] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
