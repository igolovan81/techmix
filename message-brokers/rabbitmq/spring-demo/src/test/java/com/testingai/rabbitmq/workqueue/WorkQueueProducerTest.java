package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private WorkQueueProducer workQueueProducer;

    @Test
    void send_shouldSendExactlyCountMessages() {
        workQueueProducer.send("task", 3);
        verify(rabbitTemplate, times(3))
                .convertAndSend(eq(WorkQueueConfig.QUEUE_NAME), anyString());
    }

    @Test
    void send_shouldPrependSequenceNumberToMessage() {
        workQueueProducer.send("task", 1);
        verify(rabbitTemplate).convertAndSend(WorkQueueConfig.QUEUE_NAME, "1: task");
    }
}
