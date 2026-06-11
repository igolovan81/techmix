package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send(TopicConfig.STREAMS_INPUT_TOPIC, message);
        log.info("[StreamsProducer] Sent: {}", message);
    }
}
