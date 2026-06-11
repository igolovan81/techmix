package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.WorkQueueConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class WorkQueueConsumer {

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME, containerFactory = "workQueueContainerFactory")
    public void worker1(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
        try {
            log.info("[Worker1] Processing: {}", message);
            FailureSimulator.maybeThrow("[Worker1]");
            simulateWork(message);
            log.info("[Worker1] Done: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.warn("[Worker1] Failed, requeuing for retry: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException e) {
            throw e;
        }
    }

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME, containerFactory = "workQueueContainerFactory")
    public void worker2(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
        try {
            log.info("[Worker2] Processing: {}", message);
            FailureSimulator.maybeThrow("[Worker2]");
            simulateWork(message);
            log.info("[Worker2] Done: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.warn("[Worker2] Failed, requeuing for retry: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException e) {
            throw e;
        }
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
