package com.testingai.kafka.compaction

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class CompactionProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new CompactionProducer(kafkaTemplate)

	def "send sends key value to compacted topic"() {
		when:
		producer.send("user-1", "Alice")

		then:
		1 * kafkaTemplate.send(TopicConfig.COMPACTED_TOPIC, "user-1", "Alice")
	}
}
