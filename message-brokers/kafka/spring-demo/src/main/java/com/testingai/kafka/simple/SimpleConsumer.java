package com.testingai.kafka.simple;

import com.testingai.kafka.config.TopicConfig;
import com.testingai.kafka.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimpleConsumer {

    @KafkaListener(topics = TopicConfig.SIMPLE_TOPIC, groupId = "simple-group")
    public void receive(String message) {
        FailureSimulator.maybeThrow("[SimpleConsumer]");
        log.info("[SimpleConsumer] Received: {}", message);
    }
}
