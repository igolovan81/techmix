package com.testingai.kafka.workqueue

import com.testingai.kafka.config.TopicConfig
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class WorkQueueProducerTest extends Specification {

	def kafkaTemplate = Mock(KafkaTemplate)
	def producer = new WorkQueueProducer(kafkaTemplate)

	def "send sends count messages to work topic"() {
		when:
		producer.send("task", 3)

		then:
		3 * kafkaTemplate.send(TopicConfig.WORK_TOPIC, "task")
	}
}
