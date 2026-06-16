package com.testingai.redis.controller;

import com.testingai.redis.fanout.FanoutProducer;
import com.testingai.redis.pending.PendingProducer;
import com.testingai.redis.pubsub.PubSubPublisher;
import com.testingai.redis.simple.SimpleProducer;
import com.testingai.redis.trimming.TrimmingProducer;
import com.testingai.redis.workqueue.WorkQueueProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	SimpleProducer simpleProducer;
	@MockitoBean
	WorkQueueProducer workQueueProducer;
	@MockitoBean
	FanoutProducer fanoutProducer;
	@MockitoBean
	PendingProducer pendingProducer;
	@MockitoBean
	TrimmingProducer trimmingProducer;
	@MockitoBean
	PubSubPublisher pubSubPublisher;

	@Test
	void simpleEndpointDelegatesToProducer() throws Exception {
		mockMvc.perform(post("/demo/simple").param("message", "hi")).andExpect(status().isOk());
		verify(simpleProducer).send("hi");
	}

	@Test
	void workEndpointSendsCountMessages() throws Exception {
		mockMvc.perform(post("/demo/work").param("message", "task").param("count", "3")).andExpect(status().isOk());
		verify(workQueueProducer, times(3)).send("task");
	}

	@Test
	void fanoutEndpointDelegatesToProducer() throws Exception {
		mockMvc.perform(post("/demo/fanout").param("message", "broadcast")).andExpect(status().isOk());
		verify(fanoutProducer).send("broadcast");
	}

	@Test
	void pendingEndpointSendsCountMessages() throws Exception {
		mockMvc.perform(post("/demo/pending").param("message", "hello").param("count", "2")).andExpect(status().isOk());
		verify(pendingProducer, times(2)).send("hello");
	}

	@Test
	void trimmingEndpointDelegatesToProducer() throws Exception {
		mockMvc.perform(post("/demo/trimming").param("message", "hello")).andExpect(status().isOk());
		verify(trimmingProducer).send("hello");
	}

	@Test
	void pubsubEndpointDelegatesToPublisher() throws Exception {
		mockMvc.perform(post("/demo/pubsub").param("message", "broadcast")).andExpect(status().isOk());
		verify(pubSubPublisher).publish("broadcast");
	}
}
