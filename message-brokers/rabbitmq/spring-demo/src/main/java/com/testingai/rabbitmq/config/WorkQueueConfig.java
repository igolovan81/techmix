package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkQueueConfig {

    public static final String QUEUE_NAME = "work.queue";
    public static final int MESSAGE_TTL_MS = 5000;
    public static final int DELIVERY_LIMIT = 3;

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workQueueContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
