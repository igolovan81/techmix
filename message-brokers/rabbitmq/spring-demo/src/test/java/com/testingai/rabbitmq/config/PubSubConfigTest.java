package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class PubSubConfigTest {

    private final PubSubConfig config = new PubSubConfig();

    @Test
    void pubSubQueueA_shouldHaveTtlOf5000ms() {
        Queue queue = config.pubSubQueueA();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void pubSubQueueB_shouldHaveTtlOf5000ms() {
        Queue queue = config.pubSubQueueB();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }
}
