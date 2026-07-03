package com.testingai.kafka.partitioning

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class PartitioningProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new PartitioningProducer(kafkaTemplate)

	def "send sends with key to partition topic"() {
		when:
		producer.send("error", "something broke")

		then:
		1 * kafkaTemplate.send(TopicConfig.PARTITION_TOPIC, "error", "something broke")
	}
}
