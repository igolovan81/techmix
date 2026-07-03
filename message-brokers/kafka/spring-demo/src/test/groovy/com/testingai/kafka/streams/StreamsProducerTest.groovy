package com.testingai.kafka.streams

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class StreamsProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new StreamsProducer(kafkaTemplate)

	def "send sends to streams input topic"() {
		when:
		producer.send("hello world")

		then:
		1 * kafkaTemplate.send(TopicConfig.STREAMS_INPUT_TOPIC, "hello world")
	}
}
