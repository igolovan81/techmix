package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompactionProducer {

	private final KafkaTemplate<String, String> kafkaTemplate;

	public void send(String key, String value) {
		kafkaTemplate.send(TopicConfig.COMPACTED_TOPIC, key, value);
		log.info("[CompactionProducer] Sent key={} value={}", key, value);
	}
}
