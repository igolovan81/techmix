package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StreamsProducerLegacyTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private StreamsProducer producer;

	@Test
	void send_shouldSendToStreamsInputTopic() {
		producer.send("hello world");
		verify(kafkaTemplate).send(TopicConfig.STREAMS_INPUT_TOPIC, "hello world");
	}
}
