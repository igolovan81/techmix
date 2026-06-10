package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PubSubProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String message) {
        rabbitTemplate.convertAndSend(PubSubConfig.EXCHANGE_NAME, "", message);
    }
}
