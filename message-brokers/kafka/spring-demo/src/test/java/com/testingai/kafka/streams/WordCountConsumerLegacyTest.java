package com.testingai.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class WordCountConsumerLegacyTest {

	@InjectMocks
	private WordCountConsumer consumer;

	@Test
	void receive_shouldNotThrow() {
		var record = new ConsumerRecord<>("streams-wordcount-output", 0, 0L, "hello", "3");
		assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
	}
}
