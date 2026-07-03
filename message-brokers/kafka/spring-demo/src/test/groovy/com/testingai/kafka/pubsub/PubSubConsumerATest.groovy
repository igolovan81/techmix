package com.testingai.kafka.pubsub

import spock.lang.Specification

class PubSubConsumerATest extends Specification {

	def consumer = new PubSubConsumerA()

	def "receive does not throw"() {
		when:
		consumer.receive("broadcast")

		then:
		noExceptionThrown()
	}
}
