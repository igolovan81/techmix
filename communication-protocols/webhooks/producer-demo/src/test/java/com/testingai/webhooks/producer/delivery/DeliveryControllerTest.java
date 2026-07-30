package com.testingai.webhooks.producer.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryControllerTest {

	private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(webhookDispatcher)).build();

	@Test
	void deliveries_returnsAllDeliveryAttemptsAsViews() throws Exception {
		DeliveryAttempt succeeded = new DeliveryAttempt("d1", "sub-1", "order.created", "{}", "http://a", "secret");
		succeeded.incrementAttemptCount();
		succeeded.markSucceeded();
		when(webhookDispatcher.deliveries()).thenReturn(List.of(succeeded));

		mockMvc.perform(get("/deliveries")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d1"))
				.andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$[0].attemptCount").value(1));
	}

	@Test
	void deadLetters_returnsOnlyDeadLetteredAttempts() throws Exception {
		DeliveryAttempt deadLettered = new DeliveryAttempt("d2", "sub-1", "order.created", "{}", "http://a", "secret");
		deadLettered.incrementAttemptCount();
		deadLettered.markDeadLettered();
		when(webhookDispatcher.deadLetters()).thenReturn(List.of(deadLettered));

		mockMvc.perform(get("/deliveries/dead-letter")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d2"))
				.andExpect(jsonPath("$[0].status").value("DEAD_LETTERED"));
	}
}
