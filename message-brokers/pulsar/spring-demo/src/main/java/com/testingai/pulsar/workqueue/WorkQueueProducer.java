package com.testingai.pulsar.workqueue;

import com.testingai.pulsar.config.TopicNames;
import lombok.RequiredArgsConstructor;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

	private final PulsarTemplate<String> pulsarTemplate;

	public void send(String message, int count) {
		for (int i = 0; i < count; i++) {
			pulsarTemplate.send(TopicNames.WORK_TOPIC, message);
		}
	}
}
