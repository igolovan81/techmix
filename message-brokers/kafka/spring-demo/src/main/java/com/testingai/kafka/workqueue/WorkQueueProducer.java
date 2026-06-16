package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

	private final KafkaTemplate<String, String> kafkaTemplate;

	public void send(String message, int count) {
		for (int i = 0; i < count; i++) {
			kafkaTemplate.send(TopicConfig.WORK_TOPIC, message);
		}
	}
}
