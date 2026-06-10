package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleQueueConfig {

    public static final String QUEUE_NAME = "simple.queue";

    @Bean
    public Queue simpleQueue() {
        return new Queue(QUEUE_NAME, true);
    }
}
