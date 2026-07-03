package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartitioningProducerLegacyTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private PartitioningProducer producer;

	@Test
	void send_shouldSendWithKeyToPartitionTopic() {
		producer.send("error", "something broke");
		verify(kafkaTemplate).send(TopicConfig.PARTITION_TOPIC, "error", "something broke");
	}
}
