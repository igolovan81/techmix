package com.testingai.kafka.partitioning;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class PartitioningConsumerTest {

	@InjectMocks
	private PartitioningConsumer consumer;

	@Test
	void receive_shouldNotThrow() {
		var record = new ConsumerRecord<>("partition.topic", 1, 0L, "error", "something broke");
		assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
	}
}
