package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.RoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class RoutingConsumer {

    @RabbitListener(queues = RoutingConfig.QUEUE_ALL, containerFactory = "routingContainerFactory")
    public void receiveAll(String message, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[RoutingConsumer/ALL] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[RoutingConsumer/ALL] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @RabbitListener(queues = RoutingConfig.QUEUE_ERROR, containerFactory = "routingContainerFactory")
    public void receiveError(String message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[RoutingConsumer/ERROR-ONLY] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[RoutingConsumer/ERROR-ONLY] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
