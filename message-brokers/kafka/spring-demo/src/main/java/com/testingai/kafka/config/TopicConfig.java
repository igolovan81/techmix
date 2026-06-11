package com.testingai.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class TopicConfig {

    public static final String SIMPLE_TOPIC = "simple.topic";
    public static final String WORK_TOPIC = "work.topic";
    public static final String PUBSUB_TOPIC = "pubsub.topic";
    public static final String PARTITION_TOPIC = "partition.topic";
    public static final String TX_OUTPUT_TOPIC = "tx-output.topic";
    public static final String COMPACTED_TOPIC = "compacted.topic";
    public static final String STREAMS_INPUT_TOPIC = "streams-input.topic";
    public static final String STREAMS_WORDCOUNT_OUTPUT = "streams-wordcount-output";

    @Value("${kafka.demo.replication-factor:3}")
    private int replicationFactor;

    // ── Topic declarations ──────────────────────────────────────────────────

    @Bean
    public org.apache.kafka.clients.admin.NewTopic simpleTopic() {
        return TopicBuilder.name(SIMPLE_TOPIC).partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic workTopic() {
        return TopicBuilder.name(WORK_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic pubSubTopic() {
        return TopicBuilder.name(PUBSUB_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic partitionTopic() {
        return TopicBuilder.name(PARTITION_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic txOutputTopic() {
        return TopicBuilder.name(TX_OUTPUT_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic compactedTopic() {
        return TopicBuilder.name(COMPACTED_TOPIC)
                .partitions(1)
                .replicas(replicationFactor)
                .config("cleanup.policy", "compact")
                .config("segment.ms", "5000")
                .config("min.cleanable.dirty.ratio", "0.01")
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic streamsInputTopic() {
        return TopicBuilder.name(STREAMS_INPUT_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic streamsWordcountOutput() {
        return TopicBuilder.name(STREAMS_WORDCOUNT_OUTPUT).partitions(3).replicas(replicationFactor).build();
    }

    // ── Default consumer factory ────────────────────────────────────────────
    // Declared explicitly because @ConditionalOnMissingBean(ConsumerFactory.class)
    // in Spring Boot auto-config is satisfied by readCommittedConsumerFactory,
    // so the auto-configured kafkaConsumerFactory bean would never be created.

    @Bean
    public ConsumerFactory<String, String> kafkaConsumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(null));
    }

    // ── Default listener container factory (with retry) ────────────────────

    @Bean
    @SuppressWarnings("unchecked")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("kafkaConsumerFactory") ConsumerFactory<String, String> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        configurer.configure(
                (ConcurrentKafkaListenerContainerFactory<Object, Object>) (ConcurrentKafkaListenerContainerFactory<?, ?>) factory,
                (ConsumerFactory<Object, Object>) (ConsumerFactory<?, ?>) consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(500L, 2L)));
        return factory;
    }

    // ── Non-transactional producer factory & template ──────────────────────
    // Declared explicitly because @ConditionalOnMissingBean(ProducerFactory.class) and
    // @ConditionalOnMissingBean(KafkaTemplate.class) in Spring Boot auto-config are both
    // satisfied by the transactional beans below, so the auto-configured defaults are skipped.

    @Bean
    public ProducerFactory<String, String> kafkaProducerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null));
    }

    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate(
            @Qualifier("kafkaProducerFactory") ProducerFactory<String, String> kafkaProducerFactory) {
        return new KafkaTemplate<>(kafkaProducerFactory);
    }

    // ── Transactional producer (exactly-once writes) ────────────────────────

    @Bean
    public DefaultKafkaProducerFactory<String, String> transactionalProducerFactory(
            KafkaProperties kafkaProperties) {
        var factory = new DefaultKafkaProducerFactory<String, String>(
                kafkaProperties.buildProducerProperties(null));
        factory.setTransactionIdPrefix("tx-demo-");
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> transactionalKafkaTemplate(
            @Qualifier("transactionalProducerFactory") DefaultKafkaProducerFactory<String, String> transactionalProducerFactory) {
        return new KafkaTemplate<>(transactionalProducerFactory);
    }

    // ── read_committed consumer factory (for transactional consumer) ────────

    @Bean
    public ConsumerFactory<String, String> readCommittedConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ConcurrentKafkaListenerContainerFactory<String, String> transactionalContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("readCommittedConsumerFactory") ConsumerFactory<String, String> readCommittedConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        configurer.configure(
                (ConcurrentKafkaListenerContainerFactory<Object, Object>) (ConcurrentKafkaListenerContainerFactory<?, ?>) factory,
                (ConsumerFactory<Object, Object>) (ConsumerFactory<?, ?>) readCommittedConsumerFactory);
        return factory;
    }
}
