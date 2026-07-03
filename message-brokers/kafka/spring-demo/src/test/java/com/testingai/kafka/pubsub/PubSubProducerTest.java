package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubProducerTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private PubSubProducer producer;

	@Test
	void send_shouldBroadcastToPubSubTopic() {
		producer.send("broadcast");
		verify(kafkaTemplate).send(TopicConfig.PUBSUB_TOPIC, "broadcast");
	}
}
