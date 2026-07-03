package com.testingai.kafka.pubsub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerBLegacyTest {

	@InjectMocks
	private PubSubConsumerB consumer;

	@Test
	void receive_shouldNotThrow() {
		assertThatCode(() -> consumer.receive("broadcast")).doesNotThrowAnyException();
	}
}
