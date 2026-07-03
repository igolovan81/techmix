package com.testingai.kafka.partitioning

import org.apache.kafka.clients.consumer.ConsumerRecord
import spock.lang.Specification

class PartitioningConsumerTest extends Specification {

	def consumer = new PartitioningConsumer()

	def "receive does not throw"() {
		given:
		def record = new ConsumerRecord<String, String>("partition.topic", 1, 0L, "error", "something broke")

		when:
		consumer.receive(record)

		then:
		noExceptionThrown()
	}
}
