package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerA {

    @KafkaListener(topics = TopicConfig.PUBSUB_TOPIC, groupId = "group-a")
    public void receive(String message) {
        log.info("[PubSubConsumerA] Received: {}", message);
    }
}
