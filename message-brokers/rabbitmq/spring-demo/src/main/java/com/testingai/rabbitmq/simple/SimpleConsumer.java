package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimpleConsumer {

    @RabbitListener(queues = SimpleQueueConfig.QUEUE_NAME)
    public void receive(String message) {
        log.info("[SimpleConsumer] Received: {}", message);
    }
}
