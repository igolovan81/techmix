package com.testingai.kafka.streams

import org.apache.kafka.clients.consumer.ConsumerRecord
import spock.lang.Specification

class WordCountConsumerTest extends Specification {

	def consumer = new WordCountConsumer()

	def "receive does not throw"() {
		given:
		def record = new ConsumerRecord<String, String>("streams-wordcount-output", 0, 0L, "hello", "3")

		when:
		consumer.receive(record)

		then:
		noExceptionThrown()
	}
}
