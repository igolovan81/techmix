package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerA {

    @RabbitListener(queues = PubSubConfig.QUEUE_A)
    public void receive(String message) {
        log.info("[PubSubConsumerA] Received: {}", message);
    }
}
