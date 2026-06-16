package com.testingai.pulsar.pubsub;

import com.testingai.pulsar.config.TopicNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubProducerTest {

	@Mock
	private PulsarTemplate<String> pulsarTemplate;

	@InjectMocks
	private PubSubProducer producer;

	@Test
	void send_shouldSendMessageToPubSubTopic() {
		producer.send("broadcast");
		verify(pulsarTemplate).send(TopicNames.PUBSUB_TOPIC, "broadcast");
	}
}
