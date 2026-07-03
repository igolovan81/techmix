package com.testingai.kafka.transactions

import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Specification

class TransactionalProducerTest extends Specification {

	def transactionalKafkaTemplate = Mock(KafkaTemplate)
	def producer = new TransactionalProducer(transactionalKafkaTemplate)

	def "send calls executeInTransaction"() {
		when:
		producer.send("hello", 3)

		then:
		1 * transactionalKafkaTemplate.executeInTransaction(_) >> null
	}
}
