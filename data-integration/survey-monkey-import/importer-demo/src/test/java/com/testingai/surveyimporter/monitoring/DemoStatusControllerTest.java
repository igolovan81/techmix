package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoStatusController.class)
@TestPropertySource(properties = "importer.known-survey-ids=survey-1,survey-2")
class DemoStatusControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SyncWatermarkRepository watermarkRepository;

	@MockitoBean
	private JobQueue jobQueue;

	@MockitoBean
	private DeadLetterJobRepository dlqRepository;

	@MockitoBean
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Test
	void statusReflectsQueueDlqAndCircuitBreakerState() throws Exception {
		when(watermarkRepository.findById(anyString())).thenReturn(Optional.empty());
		when(jobQueue.size()).thenReturn(3);
		when(dlqRepository.count()).thenReturn(2L);
		CircuitBreaker breaker = mock(CircuitBreaker.class);
		when(breaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
		when(circuitBreakerRegistry.circuitBreaker("surveyMonkey")).thenReturn(breaker);

		mockMvc.perform(get("/demo/status")).andExpect(status().isOk()).andExpect(jsonPath("$.queue_depth").value(3))
				.andExpect(jsonPath("$.dlq_size").value(2))
				.andExpect(jsonPath("$.circuit_breaker_state").value("CLOSED"))
				.andExpect(jsonPath("$.surveys.length()").value(2));
	}
}
