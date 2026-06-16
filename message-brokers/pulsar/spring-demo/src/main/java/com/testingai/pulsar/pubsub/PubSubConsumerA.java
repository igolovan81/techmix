package com.testingai.pulsar.pubsub;

import com.testingai.pulsar.config.TopicNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerA {

	// Different subscription name from B — each receives every message independently
	@PulsarListener(topics = TopicNames.PUBSUB_TOPIC, subscriptionName = "pubsub-sub-a")
	public void receive(String message) {
		log.info("[pubsub][sub-a] received: {}", message);
	}
}
