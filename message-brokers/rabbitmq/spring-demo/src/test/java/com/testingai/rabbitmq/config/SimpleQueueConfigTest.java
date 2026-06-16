package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleQueueConfigTest {

	private final SimpleQueueConfig config = new SimpleQueueConfig();

	@Test
	void simpleQueue_shouldHaveTtlOf5000ms() {
		Queue queue = config.simpleQueue();
		assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
	}
}
