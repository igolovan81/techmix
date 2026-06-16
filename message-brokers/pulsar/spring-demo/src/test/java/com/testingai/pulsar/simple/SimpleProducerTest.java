package com.testingai.pulsar.simple;

import com.testingai.pulsar.config.TopicNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerTest {

	@Mock
	private PulsarTemplate<String> pulsarTemplate;

	@InjectMocks
	private SimpleProducer producer;

	@Test
	void send_shouldSendMessageToSimpleTopic() {
		producer.send("hello");
		verify(pulsarTemplate).send(TopicNames.SIMPLE_TOPIC, "hello");
	}
}
