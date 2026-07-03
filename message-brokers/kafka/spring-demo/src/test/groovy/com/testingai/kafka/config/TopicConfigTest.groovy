package com.testingai.kafka.config

import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

class TopicConfigTest extends Specification {

	TopicConfig config

	def setup() {
		config = new TopicConfig()
		ReflectionTestUtils.setField(config, "replicationFactor", 1)
	}

	def "simpleTopic has one partition"() {
		when:
		def topic = config.simpleTopic()

		then:
		topic.name() == "simple.topic"
		topic.numPartitions() == 1
	}

	def "workTopic has three partitions"() {
		when:
		def topic = config.workTopic()

		then:
		topic.name() == "work.topic"
		topic.numPartitions() == 3
	}

	def "compactedTopic has compact policy"() {
		when:
		def topic = config.compactedTopic()

		then:
		topic.configs().get("cleanup.policy") == "compact"
	}
}
