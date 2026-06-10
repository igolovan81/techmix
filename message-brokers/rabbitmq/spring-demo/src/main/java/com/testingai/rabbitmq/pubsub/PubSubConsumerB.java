package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerB {

    @RabbitListener(queues = PubSubConfig.QUEUE_B)
    public void receive(String message) {
        log.info("[PubSubConsumerB] Received: {}", message);
    }
}
