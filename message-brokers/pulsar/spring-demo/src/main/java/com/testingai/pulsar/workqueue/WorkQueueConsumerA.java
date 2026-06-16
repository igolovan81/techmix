package com.testingai.pulsar.workqueue;

import com.testingai.pulsar.config.TopicNames;
import com.testingai.pulsar.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkQueueConsumerA {

	// Shared subscription — A and B compete for each message (round-robin delivery)
	@PulsarListener(topics = TopicNames.WORK_TOPIC, subscriptionName = "work-sub", subscriptionType = SubscriptionType.Shared)
	public void receive(String message) {
		log.info("[work][A] processing: {}", message);
		FailureSimulator.maybeThrow("[work][A]");
		simulateWork(message);
		log.info("[work][A] done: {}", message);
	}

	private void simulateWork(String message) {
		long dots = message.chars().filter(c -> c == '.').count();
		try {
			Thread.sleep(dots * 1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
