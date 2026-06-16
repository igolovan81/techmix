package com.testingai.pulsar.workqueue;

import com.testingai.pulsar.config.TopicNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.pulsar.core.PulsarTemplate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkQueueProducerTest {

	@Mock
	private PulsarTemplate<String> pulsarTemplate;

	@InjectMocks
	private WorkQueueProducer producer;

	@Test
	void send_shouldSendCountMessagesToWorkTopic() {
		producer.send("task..", 3);
		verify(pulsarTemplate, times(3)).send(TopicNames.WORK_TOPIC, "task..");
	}
}
