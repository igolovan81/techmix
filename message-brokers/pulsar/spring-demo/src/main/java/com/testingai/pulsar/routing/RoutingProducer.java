package com.testingai.pulsar.routing;

import com.testingai.pulsar.config.TopicNames;
import lombok.RequiredArgsConstructor;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingProducer {

	private final PulsarTemplate<String> pulsarTemplate;

	public void send(String key, String message) {
		// Key_Shared subscription guarantees all messages with the same key
		// are always delivered to the same consumer instance
		pulsarTemplate.newMessage(message).withTopic(TopicNames.ROUTING_TOPIC).withMessageCustomizer(mb -> mb.key(key))
				.send();
	}
}
