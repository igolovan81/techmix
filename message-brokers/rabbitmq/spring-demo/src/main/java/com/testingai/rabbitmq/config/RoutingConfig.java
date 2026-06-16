package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

	public static final String EXCHANGE_NAME = "routing.direct";
	public static final String QUEUE_ALL = "routing.queue.all";
	public static final String QUEUE_ERROR = "routing.queue.error";
	public static final String KEY_INFO = "info";
	public static final String KEY_WARNING = "warning";
	public static final String KEY_ERROR = "error";
	public static final int MESSAGE_TTL_MS = 5000;
	public static final int DELIVERY_LIMIT = 3;

	@Bean
	public DirectExchange routingExchange() {
		return new DirectExchange(EXCHANGE_NAME, true, false);
	}

	@Bean
	public Queue routingQueueAll() {
		return QueueBuilder.durable(QUEUE_ALL).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
	}

	@Bean
	public Queue routingQueueError() {
		return QueueBuilder.durable(QUEUE_ERROR).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
	}

	@Bean
	public Binding bindingAllInfo(DirectExchange routingExchange, Queue routingQueueAll) {
		return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_INFO);
	}

	@Bean
	public Binding bindingAllWarning(DirectExchange routingExchange, Queue routingQueueAll) {
		return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_WARNING);
	}

	@Bean
	public Binding bindingAllError(DirectExchange routingExchange, Queue routingQueueAll) {
		return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_ERROR);
	}

	@Bean
	public Binding bindingErrorOnly(DirectExchange routingExchange, Queue routingQueueError) {
		return BindingBuilder.bind(routingQueueError).to(routingExchange).with(KEY_ERROR);
	}

	@Bean
	public SimpleRabbitListenerContainerFactory routingContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		return factory;
	}
}
