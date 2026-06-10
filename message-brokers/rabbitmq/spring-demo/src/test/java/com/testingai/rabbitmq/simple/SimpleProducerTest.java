package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SimpleProducer simpleProducer;

    @Test
    void send_shouldConvertAndSendToSimpleQueue() {
        simpleProducer.send("hello");
        verify(rabbitTemplate).convertAndSend(SimpleQueueConfig.QUEUE_NAME, "hello");
    }
}
