package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
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
	private RoutingProducer routingProducer;

	@Test
	void simple_shouldReturn200AndDelegateSend() throws Exception {
		mockMvc.perform(post("/demo/simple").param("message", "hello")).andExpect(status().isOk());
		verify(simpleProducer).send("hello");
	}

	@Test
	void work_shouldReturn200AndDelegateSendWithDefaultCount() throws Exception {
		mockMvc.perform(post("/demo/work").param("message", "task")).andExpect(status().isOk());
		verify(workQueueProducer).send("task", 5);
	}

	@Test
	void work_shouldPassExplicitCount() throws Exception {
		mockMvc.perform(post("/demo/work").param("message", "task..").param("count", "3")).andExpect(status().isOk());
		verify(workQueueProducer).send("task..", 3);
	}

	@Test
	void pubsub_shouldReturn200AndDelegateSend() throws Exception {
		mockMvc.perform(post("/demo/pubsub").param("message", "broadcast")).andExpect(status().isOk());
		verify(pubSubProducer).send("broadcast");
	}

	@Test
	void routing_shouldReturn200AndDelegateSend() throws Exception {
		mockMvc.perform(post("/demo/routing").param("key", "error").param("message", "boom"))
				.andExpect(status().isOk());
		verify(routingProducer).send("error", "boom");
	}
}
