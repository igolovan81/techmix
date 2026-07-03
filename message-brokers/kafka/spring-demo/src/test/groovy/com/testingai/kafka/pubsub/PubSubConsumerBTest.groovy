package com.testingai.kafka.pubsub

import spock.lang.Specification

class PubSubConsumerBTest extends Specification {

	def consumer = new PubSubConsumerB()

	def "receive does not throw"() {
		when:
		consumer.receive("broadcast")

		then:
		noExceptionThrown()
	}
}
