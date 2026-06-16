package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingProducer {

	private final RabbitTemplate rabbitTemplate;

	public void send(String routingKey, String message) {
		rabbitTemplate.convertAndSend(RoutingConfig.EXCHANGE_NAME, routingKey, message);
	}
}
