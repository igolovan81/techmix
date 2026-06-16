package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class PubSubConfigTest {

	private final PubSubConfig config = new PubSubConfig();

	@Test
	void pubSubQueueA_shouldHaveTtlOf5000ms() {
		Queue queue = config.pubSubQueueA();
		assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
	}

	@Test
	void pubSubQueueB_shouldHaveTtlOf5000ms() {
		Queue queue = config.pubSubQueueB();
		assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
	}

	@Test
	void pubSubQueueA_shouldHaveDlxRoutingKeyToRetryQueue() {
		Queue queue = config.pubSubQueueA();
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.RETRY_QUEUE_A);
	}

	@Test
	void pubSubQueueB_shouldHaveDlxRoutingKeyToRetryQueue() {
		Queue queue = config.pubSubQueueB();
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.RETRY_QUEUE_B);
	}

	@Test
	void pubSubRetryQueueA_shouldHaveTtlOf2000ms() {
		Queue queue = config.pubSubRetryQueueA();
		assertThat(queue.getArguments()).containsEntry("x-message-ttl", PubSubConfig.RETRY_DELAY_MS);
	}

	@Test
	void pubSubRetryQueueA_shouldHaveDlxBackToMainQueue() {
		Queue queue = config.pubSubRetryQueueA();
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.QUEUE_A);
	}

	@Test
	void pubSubRetryQueueB_shouldHaveTtlOf2000ms() {
		Queue queue = config.pubSubRetryQueueB();
		assertThat(queue.getArguments()).containsEntry("x-message-ttl", PubSubConfig.RETRY_DELAY_MS);
	}

	@Test
	void pubSubRetryQueueB_shouldHaveDlxBackToMainQueue() {
		Queue queue = config.pubSubRetryQueueB();
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
		assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.QUEUE_B);
	}
}
