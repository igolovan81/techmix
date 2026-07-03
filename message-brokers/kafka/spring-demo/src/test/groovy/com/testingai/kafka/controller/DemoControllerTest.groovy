package com.testingai.kafka.controller

import com.testingai.kafka.compaction.CompactionProducer
import com.testingai.kafka.partitioning.PartitioningProducer
import com.testingai.kafka.pubsub.PubSubProducer
import com.testingai.kafka.simple.SimpleProducer
import com.testingai.kafka.streams.StreamsProducer
import com.testingai.kafka.transactions.TransactionalProducer
import com.testingai.kafka.workqueue.WorkQueueProducer
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class DemoControllerTest extends Specification {

	def simpleProducer = Mock(SimpleProducer)
	def workQueueProducer = Mock(WorkQueueProducer)
	def pubSubProducer = Mock(PubSubProducer)
	def partitioningProducer = Mock(PartitioningProducer)
	def transactionalProducer = Mock(TransactionalProducer)
	def compactionProducer = Mock(CompactionProducer)
	def streamsProducer = Mock(StreamsProducer)

	MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(simpleProducer, workQueueProducer,
			pubSubProducer, partitioningProducer, transactionalProducer, compactionProducer, streamsProducer)).build()

	def "simple endpoint returns 200 and delegates"() {
		when:
		mockMvc.perform(post("/demo/simple").param("message", "hello")).andExpect(status().isOk())

		then:
		1 * simpleProducer.send("hello")
	}

	def "work endpoint returns 200 with default count"() {
		when:
		mockMvc.perform(post("/demo/work").param("message", "task")).andExpect(status().isOk())

		then:
		1 * workQueueProducer.send("task", 5)
	}

	def "work endpoint passes explicit count"() {
		when:
		mockMvc.perform(post("/demo/work").param("message", "task..").param("count", "3")).andExpect(status().isOk())

		then:
		1 * workQueueProducer.send("task..", 3)
	}

	def "pubsub endpoint returns 200 and delegates"() {
		when:
		mockMvc.perform(post("/demo/pubsub").param("message", "broadcast")).andExpect(status().isOk())

		then:
		1 * pubSubProducer.send("broadcast")
	}

	def "partition endpoint returns 200 and delegates"() {
		when:
		mockMvc.perform(post("/demo/partition").param("key", "error").param("message", "boom"))
				.andExpect(status().isOk())

		then:
		1 * partitioningProducer.send("error", "boom")
	}

	def "transaction endpoint returns 200 with default count"() {
		when:
		mockMvc.perform(post("/demo/transaction").param("message", "hello")).andExpect(status().isOk())

		then:
		1 * transactionalProducer.send("hello", 3)
	}

	def "compaction endpoint returns 200 and delegates"() {
		when:
		mockMvc.perform(post("/demo/compaction").param("key", "user-1").param("value", "Alice"))
				.andExpect(status().isOk())

		then:
		1 * compactionProducer.send("user-1", "Alice")
	}

	def "streams endpoint returns 200 and delegates"() {
		when:
		mockMvc.perform(post("/demo/streams").param("message", "hello world")).andExpect(status().isOk())

		then:
		1 * streamsProducer.send("hello world")
	}
}
