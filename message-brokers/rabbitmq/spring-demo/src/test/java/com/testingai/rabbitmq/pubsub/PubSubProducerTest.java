package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PubSubProducer pubSubProducer;

    @Test
    void send_shouldPublishToFanoutExchangeWithEmptyRoutingKey() {
        pubSubProducer.send("broadcast");
        verify(rabbitTemplate).convertAndSend(PubSubConfig.EXCHANGE_NAME, "", "broadcast");
    }
}
