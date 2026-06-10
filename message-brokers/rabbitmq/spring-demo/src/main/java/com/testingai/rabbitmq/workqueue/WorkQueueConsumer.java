package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkQueueConsumer {

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME)
    public void worker1(String message) throws InterruptedException {
        log.info("[Worker1] Processing: {}", message);
        simulateWork(message);
        log.info("[Worker1] Done: {}", message);
    }

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME)
    public void worker2(String message) throws InterruptedException {
        log.info("[Worker2] Processing: {}", message);
        simulateWork(message);
        log.info("[Worker2] Done: {}", message);
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
