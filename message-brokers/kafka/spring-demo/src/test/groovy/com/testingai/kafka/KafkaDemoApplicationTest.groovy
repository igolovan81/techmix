package com.testingai.kafka

import spock.lang.Specification

class KafkaDemoApplicationTest extends Specification {

	def "main class exists"() {
		expect:
		new KafkaDemoApplication()
	}
}
