package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoutingConsumer {

    @RabbitListener(queues = RoutingConfig.QUEUE_ALL)
    public void receiveAll(String message) {
        log.info("[RoutingConsumer/ALL] Received: {}", message);
    }

    @RabbitListener(queues = RoutingConfig.QUEUE_ERROR)
    public void receiveError(String message) {
        log.info("[RoutingConsumer/ERROR-ONLY] Received: {}", message);
    }
}
