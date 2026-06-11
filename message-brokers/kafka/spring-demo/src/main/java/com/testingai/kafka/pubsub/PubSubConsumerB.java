package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerB {

    @KafkaListener(topics = TopicConfig.PUBSUB_TOPIC, groupId = "group-b")
    public void receive(String message) {
        log.info("[PubSubConsumerB] Received: {}", message);
    }
}
