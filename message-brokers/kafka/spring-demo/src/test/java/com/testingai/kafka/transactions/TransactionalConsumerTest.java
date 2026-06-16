package com.testingai.kafka.transactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class TransactionalConsumerTest {

	@InjectMocks
	private TransactionalConsumer consumer;

	@Test
	void receive_shouldNotThrow() {
		assertThatCode(() -> consumer.receive("committed-message")).doesNotThrowAnyException();
	}
}
