package com.testingai.redis.pubsub;

import org.junit.jupiter.api.Test;

class PubSubSubscriberATest {
	@Test
	void onMessageLogsPayload() {
		var subscriber = new PubSubSubscriberA();
		subscriber.onMessage(null, "hello".getBytes());
	}
}
