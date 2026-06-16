package com.testingai.pulsar.pubsub;

import com.testingai.pulsar.config.TopicNames;
import lombok.RequiredArgsConstructor;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PubSubProducer {

	private final PulsarTemplate<String> pulsarTemplate;

	public void send(String message) {
		pulsarTemplate.send(TopicNames.PUBSUB_TOPIC, message);
	}
}
