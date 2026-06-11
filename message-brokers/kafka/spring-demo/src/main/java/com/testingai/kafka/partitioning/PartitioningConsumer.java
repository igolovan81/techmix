package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PartitioningConsumer {

    @KafkaListener(topics = TopicConfig.PARTITION_TOPIC, groupId = "partition-group")
    public void receive(ConsumerRecord<String, String> record) {
        log.info("[PartitioningConsumer] key={} partition={} value={}",
                record.key(), record.partition(), record.value());
    }
}
