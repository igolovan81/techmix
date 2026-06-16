package com.testingai.pulsar.simple;

import com.testingai.pulsar.config.TopicNames;
import lombok.RequiredArgsConstructor;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleProducer {

	private final PulsarTemplate<String> pulsarTemplate;

	public void send(String message) {
		pulsarTemplate.send(TopicNames.SIMPLE_TOPIC, message);
	}
}
