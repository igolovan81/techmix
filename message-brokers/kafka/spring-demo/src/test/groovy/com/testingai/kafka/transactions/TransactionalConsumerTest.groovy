package com.testingai.kafka.transactions

import spock.lang.Specification

class TransactionalConsumerTest extends Specification {

	def consumer = new TransactionalConsumer()

	def "receive does not throw"() {
		when:
		consumer.receive("committed-message")

		then:
		noExceptionThrown()
	}
}
