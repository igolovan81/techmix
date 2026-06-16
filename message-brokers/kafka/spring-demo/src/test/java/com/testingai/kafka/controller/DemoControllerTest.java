package com.testingai.kafka.controller;

import com.testingai.kafka.compaction.CompactionProducer;
import com.testingai.kafka.partitioning.PartitioningProducer;
import com.testingai.kafka.pubsub.PubSubProducer;
import com.testingai.kafka.simple.SimpleProducer;
import com.testingai.kafka.streams.StreamsProducer;
import com.testingai.kafka.transactions.TransactionalProducer;
import com.testingai.kafka.workqueue.WorkQueueProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SimpleProducer simpleProducer;
	@MockitoBean
	private WorkQueueProducer workQueueProducer;
	@MockitoBean
	private PubSubProducer pubSubProducer;
	@MockitoBean
	private PartitioningProducer partitioningProducer;
	@MockitoBean
	private TransactionalProducer transactionalProducer;
	@MockitoBean
	private CompactionProducer compactionProducer;
	@MockitoBean
	private StreamsProducer streamsProducer;

	@Test
	void simple_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/simple").param("message", "hello")).andExpect(status().isOk());
		verify(simpleProducer).send("hello");
	}

	@Test
	void work_shouldReturn200WithDefaultCount() throws Exception {
		mockMvc.perform(post("/demo/work").param("message", "task")).andExpect(status().isOk());
		verify(workQueueProducer).send("task", 5);
	}

	@Test
	void work_shouldPassExplicitCount() throws Exception {
		mockMvc.perform(post("/demo/work").param("message", "task..").param("count", "3")).andExpect(status().isOk());
		verify(workQueueProducer).send("task..", 3);
	}

	@Test
	void pubsub_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/pubsub").param("message", "broadcast")).andExpect(status().isOk());
		verify(pubSubProducer).send("broadcast");
	}

	@Test
	void partition_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/partition").param("key", "error").param("message", "boom"))
				.andExpect(status().isOk());
		verify(partitioningProducer).send("error", "boom");
	}

	@Test
	void transaction_shouldReturn200WithDefaultCount() throws Exception {
		mockMvc.perform(post("/demo/transaction").param("message", "hello")).andExpect(status().isOk());
		verify(transactionalProducer).send("hello", 3);
	}

	@Test
	void compaction_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/compaction").param("key", "user-1").param("value", "Alice"))
				.andExpect(status().isOk());
		verify(compactionProducer).send("user-1", "Alice");
	}

	@Test
	void streams_shouldReturn200AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/streams").param("message", "hello world")).andExpect(status().isOk());
		verify(streamsProducer).send("hello world");
	}
}
