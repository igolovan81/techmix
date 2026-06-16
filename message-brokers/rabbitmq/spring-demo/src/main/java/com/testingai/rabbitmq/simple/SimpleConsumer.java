package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.SimpleQueueConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SimpleConsumer {

	@RabbitListener(queues = SimpleQueueConfig.QUEUE_NAME, containerFactory = "simpleContainerFactory")
	public void receive(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
			@Header(AmqpHeaders.REDELIVERED) boolean redelivered) throws IOException {
		try {
			FailureSimulator.maybeThrow("[SimpleConsumer]");
			log.info("[SimpleConsumer] Received: {}", message);
			channel.basicAck(deliveryTag, false);
		} catch (RuntimeException e) {
			log.warn("[SimpleConsumer] Failed (redelivered={}), requeue={}: {}", redelivered, !redelivered,
					e.getMessage());
			channel.basicNack(deliveryTag, false, !redelivered);
		} catch (IOException e) {
			throw e;
		}
	}
}
