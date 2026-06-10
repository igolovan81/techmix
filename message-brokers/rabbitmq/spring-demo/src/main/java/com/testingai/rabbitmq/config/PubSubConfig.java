package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

    public static final String EXCHANGE_NAME = "pubsub.fanout";
    public static final String QUEUE_A = "pubsub.queue.a";
    public static final String QUEUE_B = "pubsub.queue.b";

    @Bean
    public FanoutExchange pubSubExchange() {
        return new FanoutExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue pubSubQueueA() {
        return new Queue(QUEUE_A, true);
    }

    @Bean
    public Queue pubSubQueueB() {
        return new Queue(QUEUE_B, true);
    }

    @Bean
    public Binding bindingA(FanoutExchange pubSubExchange, Queue pubSubQueueA) {
        return BindingBuilder.bind(pubSubQueueA).to(pubSubExchange);
    }

    @Bean
    public Binding bindingB(FanoutExchange pubSubExchange, Queue pubSubQueueB) {
        return BindingBuilder.bind(pubSubQueueB).to(pubSubExchange);
    }
}
