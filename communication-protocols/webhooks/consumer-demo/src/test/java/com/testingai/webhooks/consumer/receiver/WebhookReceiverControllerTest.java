package com.testingai.webhooks.consumer.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.webhooks.consumer.failure.FailureSimulationState;
import com.testingai.webhooks.consumer.security.HmacVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookReceiverControllerTest {

	private static final String SECRET = "test-secret";
	private static final String BODY = "{\"eventType\":\"order.created\",\"orderId\":\"order-1\","
			+ "\"occurredAt\":\"2026-01-01T00:00:00Z\",\"data\":{}}";
	private static final String VALID_SIGNATURE = "sha256="
			+ "77fce76c2cc7b5e4ba92a48bf14b80408e44d0d7caf9d5c8b2054c6df246ac54";

	private final HmacVerifier hmacVerifier = new HmacVerifier();
	private final FailureSimulationState failureSimulationState = new FailureSimulationState();
	private final ReceivedEventStore receivedEventStore = new ReceivedEventStore();
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebhookReceiverController(hmacVerifier,
			failureSimulationState, receivedEventStore, objectMapper, SECRET)).build();

	@Test
	void receive_returns200_andRecordsEvent_whenSignatureValid() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON).header("X-Webhook-Id", "d1")
				.header("X-Webhook-Event", "order.created").header("X-Webhook-Signature", VALID_SIGNATURE)
				.content(BODY)).andExpect(status().isOk());

		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::deliveryId).containsExactly("d1");
		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::duplicate).containsExactly(false);
	}

	@Test
	void receive_returns401_andDoesNotRecord_whenSignatureInvalid() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON).header("X-Webhook-Id", "d2")
				.header("X-Webhook-Event", "order.created").header("X-Webhook-Signature", "sha256=deadbeef")
				.content(BODY)).andExpect(status().isUnauthorized());

		assertThat(receivedEventStore.all()).isEmpty();
	}

	@Test
	void receive_returns500_andDoesNotRecord_whenFailureSimulationArmed() throws Exception {
		failureSimulationState.arm(1);

		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON).header("X-Webhook-Id", "d3")
				.header("X-Webhook-Event", "order.created").header("X-Webhook-Signature", VALID_SIGNATURE)
				.content(BODY)).andExpect(status().isInternalServerError());

		assertThat(receivedEventStore.all()).isEmpty();
		assertThat(failureSimulationState.remaining()).isEqualTo(0);
	}

	@Test
	void receive_marksSecondDeliveryOfSameId_asDuplicate() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON).header("X-Webhook-Id", "d4")
				.header("X-Webhook-Event", "order.created").header("X-Webhook-Signature", VALID_SIGNATURE)
				.content(BODY)).andExpect(status().isOk());

		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON).header("X-Webhook-Id", "d4")
				.header("X-Webhook-Event", "order.created").header("X-Webhook-Signature", VALID_SIGNATURE)
				.content(BODY)).andExpect(status().isOk());

		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::duplicate).containsExactly(false, true);
	}
}
