package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

	public static final String EXCHANGE_NAME = "pubsub.fanout";
	public static final String QUEUE_A = "pubsub.queue.a";
	public static final String QUEUE_B = "pubsub.queue.b";
	public static final String RETRY_QUEUE_A = "pubsub.retry.queue.a";
	public static final String RETRY_QUEUE_B = "pubsub.retry.queue.b";
	public static final int MESSAGE_TTL_MS = 5000;
	public static final int RETRY_DELAY_MS = 2000;
	public static final int MAX_RETRIES = 3;

	@Bean
	public FanoutExchange pubSubExchange() {
		return new FanoutExchange(EXCHANGE_NAME, true, false);
	}

	@Bean
	public Queue pubSubQueueA() {
		return QueueBuilder.durable(QUEUE_A).ttl(MESSAGE_TTL_MS).deadLetterExchange("")
				.deadLetterRoutingKey(RETRY_QUEUE_A).build();
	}

	@Bean
	public Queue pubSubQueueB() {
		return QueueBuilder.durable(QUEUE_B).ttl(MESSAGE_TTL_MS).deadLetterExchange("")
				.deadLetterRoutingKey(RETRY_QUEUE_B).build();
	}

	@Bean
	public Queue pubSubRetryQueueA() {
		return QueueBuilder.durable(RETRY_QUEUE_A).ttl(RETRY_DELAY_MS).deadLetterExchange("")
				.deadLetterRoutingKey(QUEUE_A).build();
	}

	@Bean
	public Queue pubSubRetryQueueB() {
		return QueueBuilder.durable(RETRY_QUEUE_B).ttl(RETRY_DELAY_MS).deadLetterExchange("")
				.deadLetterRoutingKey(QUEUE_B).build();
	}

	@Bean
	public Binding bindingA(FanoutExchange pubSubExchange, Queue pubSubQueueA) {
		return BindingBuilder.bind(pubSubQueueA).to(pubSubExchange);
	}

	@Bean
	public Binding bindingB(FanoutExchange pubSubExchange, Queue pubSubQueueB) {
		return BindingBuilder.bind(pubSubQueueB).to(pubSubExchange);
	}

	@Bean
	public SimpleRabbitListenerContainerFactory pubSubContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		return factory;
	}
}
