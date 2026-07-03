package com.testingai.kafka.simple

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class SimpleProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new SimpleProducer(kafkaTemplate)

	def "send sends message to simple topic"() {
		when:
		producer.send("hello")

		then:
		1 * kafkaTemplate.send(TopicConfig.SIMPLE_TOPIC, "hello")
	}
}
