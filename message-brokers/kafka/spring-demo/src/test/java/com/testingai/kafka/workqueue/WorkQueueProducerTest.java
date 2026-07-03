package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkQueueProducerTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private WorkQueueProducer producer;

	@Test
	void send_shouldSendCountMessagesToWorkTopic() {
		producer.send("task", 3);
		verify(kafkaTemplate, times(3)).send(TopicConfig.WORK_TOPIC, "task");
	}
}
