package com.testingai.kafka.compaction

import org.apache.kafka.clients.consumer.ConsumerRecord
import spock.lang.Specification

class CompactionConsumerTest extends Specification {

	def consumer = new CompactionConsumer()

	def "receive does not throw"() {
		given:
		def record = new ConsumerRecord<String, String>("compacted.topic", 0, 0L, "user-1", "Alice")

		when:
		consumer.receive(record)

		then:
		noExceptionThrown()
	}
}
