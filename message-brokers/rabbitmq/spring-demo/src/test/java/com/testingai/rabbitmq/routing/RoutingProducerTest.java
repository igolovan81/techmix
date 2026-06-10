package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoutingProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RoutingProducer routingProducer;

    @Test
    void send_shouldPublishToDirectExchangeWithGivenRoutingKey() {
        routingProducer.send("error", "something broke");
        verify(rabbitTemplate).convertAndSend(RoutingConfig.EXCHANGE_NAME, "error", "something broke");
    }

    @Test
    void send_shouldPublishToDirectExchangeWithInfoKey() {
        routingProducer.send("info", "all good");
        verify(rabbitTemplate).convertAndSend(RoutingConfig.EXCHANGE_NAME, "info", "all good");
    }
}
