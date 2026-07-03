package com.testingai.kafka.simple;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerLegacyTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private SimpleProducer producer;

	@Test
	void send_shouldSendMessageToSimpleTopic() {
		producer.send("hello");
		verify(kafkaTemplate).send(TopicConfig.SIMPLE_TOPIC, "hello");
	}
}
