package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class WorkQueueConfigTest {

    private final WorkQueueConfig config = new WorkQueueConfig();

    @Test
    void workQueue_shouldHaveTtlOf5000ms() {
        Queue queue = config.workQueue();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }
}
