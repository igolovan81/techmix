package com.testingai.redis.pubsub;

import org.junit.jupiter.api.Test;

class PubSubSubscriberBTest {
    @Test
    void onMessageLogsPayload() {
        var subscriber = new PubSubSubscriberB();
        subscriber.onMessage(null, "hello".getBytes());
    }
}
