package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompactionProducerLegacyTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private CompactionProducer producer;

	@Test
	void send_shouldSendKeyValueToCompactedTopic() {
		producer.send("user-1", "Alice");
		verify(kafkaTemplate).send(TopicConfig.COMPACTED_TOPIC, "user-1", "Alice");
	}
}
