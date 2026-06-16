package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleProducer {

	private final RabbitTemplate rabbitTemplate;

	public void send(String message) {
		rabbitTemplate.convertAndSend(SimpleQueueConfig.QUEUE_NAME, message);
	}
}
