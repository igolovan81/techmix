package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingConfigTest {

    private final RoutingConfig config = new RoutingConfig();

    @Test
    void routingQueueAll_shouldHaveTtlOf5000ms() {
        Queue queue = config.routingQueueAll();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void routingQueueError_shouldHaveTtlOf5000ms() {
        Queue queue = config.routingQueueError();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }
}
