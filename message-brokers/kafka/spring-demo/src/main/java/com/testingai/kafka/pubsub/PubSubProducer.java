package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PubSubProducer {

	private final KafkaTemplate<String, String> kafkaTemplate;

	public void send(String message) {
		kafkaTemplate.send(TopicConfig.PUBSUB_TOPIC, message);
	}
}
