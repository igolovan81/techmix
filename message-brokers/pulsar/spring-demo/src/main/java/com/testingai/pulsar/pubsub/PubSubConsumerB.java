package com.testingai.pulsar.pubsub;

import com.testingai.pulsar.config.TopicNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerB {

	// Different subscription name from A — each receives every message independently
	@PulsarListener(topics = TopicNames.PUBSUB_TOPIC, subscriptionName = "pubsub-sub-b")
	public void receive(String message) {
		log.info("[pubsub][sub-b] received: {}", message);
	}
}
