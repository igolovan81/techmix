package com.testingai.pulsar.routing;

import com.testingai.pulsar.config.TopicNames;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoutingConsumerB {

	// Same subscription + Key_Shared as A — each key is sticky to one consumer instance
	@PulsarListener(topics = TopicNames.ROUTING_TOPIC, subscriptionName = "routing-sub", subscriptionType = SubscriptionType.Key_Shared)
	public void receive(Message<String> message) {
		log.info("[routing][B] key={} value={}", message.getKey(), message.getValue());
	}
}
