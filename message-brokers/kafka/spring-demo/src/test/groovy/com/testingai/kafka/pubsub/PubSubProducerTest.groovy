package com.testingai.kafka.pubsub

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class PubSubProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new PubSubProducer(kafkaTemplate)

	def "send broadcasts to pubsub topic"() {
		when:
		producer.send("broadcast")

		then:
		1 * kafkaTemplate.send(TopicConfig.PUBSUB_TOPIC, "broadcast")
	}
}
