package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkQueueConfig {

    public static final String QUEUE_NAME = "work.queue";

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().build();
    }
}
