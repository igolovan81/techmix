package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartitioningProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String key, String message) {
        kafkaTemplate.send(TopicConfig.PARTITION_TOPIC, key, message);
    }
}
