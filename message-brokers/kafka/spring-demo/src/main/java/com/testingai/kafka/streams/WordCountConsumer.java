package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WordCountConsumer {

	@KafkaListener(topics = TopicConfig.STREAMS_WORDCOUNT_OUTPUT, groupId = "wordcount-group")
	public void receive(ConsumerRecord<String, String> record) {
		log.info("[WordCountConsumer] word={} count={}", record.key(), record.value());
	}
}
