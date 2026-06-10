package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String message, int count) {
        for (int i = 1; i <= count; i++) {
            rabbitTemplate.convertAndSend(WorkQueueConfig.QUEUE_NAME, i + ": " + message);
        }
    }
}
