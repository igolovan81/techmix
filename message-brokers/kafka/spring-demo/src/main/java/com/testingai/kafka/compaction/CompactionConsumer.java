package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompactionConsumer {

	@KafkaListener(topics = TopicConfig.COMPACTED_TOPIC, groupId = "compaction-group")
	public void receive(ConsumerRecord<String, String> record) {
		log.info("[CompactionConsumer] key={} value={}", record.key(), record.value());
	}
}
