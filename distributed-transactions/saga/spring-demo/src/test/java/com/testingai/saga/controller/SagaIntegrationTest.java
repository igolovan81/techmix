package com.testingai.saga.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.saga.choreography.InventoryParticipant;
import com.testingai.saga.choreography.OrderParticipant;
import com.testingai.saga.choreography.PaymentParticipant;
import com.testingai.saga.choreography.SagaLog;
import com.testingai.saga.choreography.ShippingParticipant;
import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;
import com.testingai.saga.orchestration.SagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import({ OrderParticipant.class, InventoryParticipant.class, PaymentParticipant.class, ShippingParticipant.class,
		SagaLog.class, SagaOrchestrator.class, com.testingai.saga.orchestration.InventoryParticipant.class,
		com.testingai.saga.orchestration.PaymentParticipant.class,
		com.testingai.saga.orchestration.ShippingParticipant.class })
class SagaIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void choreographyCheckout_happyPath_shouldConfirmOrder() throws Exception {
		String orderId = checkoutChoreography(null);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.timeline", hasSize(5)))
				.andExpect(jsonPath("$.timeline[4].step").value("ORDER_CONFIRMED"));
	}

	@Test
	void choreographyCheckout_paymentFailure_shouldCascadeCompensationAndCancelOrder() throws Exception {
		String orderId = checkoutChoreography(SagaStep.PROCESS_PAYMENT);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.timeline[*].step", contains("ORDER_CREATED", "INVENTORY_RESERVED",
						"PAYMENT_FAILED", "INVENTORY_RELEASED", "ORDER_CANCELLED")));
	}

	@Test
	void choreographyCheckout_shippingFailure_shouldCascadeCompensationThroughPaymentAndInventory() throws Exception {
		String orderId = checkoutChoreography(SagaStep.ARRANGE_SHIPPING);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.timeline[*].step", contains("ORDER_CREATED", "INVENTORY_RESERVED",
						"PAYMENT_PROCESSED", "SHIPMENT_FAILED", "PAYMENT_REFUNDED", "INVENTORY_RELEASED",
						"ORDER_CANCELLED")));
	}

	@Test
	void choreographyOrder_unknownOrderId_shouldReturn404() throws Exception {
		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", "missing")).andExpect(status().isNotFound());
	}

	@Test
	void orchestrationCheckout_happyPath_shouldConfirmOrder() throws Exception {
		mockMvc.perform(post("/demo/saga/orchestration/checkout").contentType("application/json")
				.content(objectMapper.writeValueAsString(sampleRequest(null)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.failedStep").value(nullValue()))
				.andExpect(jsonPath("$.compensatedSteps", hasSize(0)));
	}

	@Test
	void orchestrationCheckout_shippingFailure_shouldCompensatePaymentThenInventory() throws Exception {
		mockMvc.perform(post("/demo/saga/orchestration/checkout").contentType("application/json")
				.content(objectMapper.writeValueAsString(sampleRequest(SagaStep.ARRANGE_SHIPPING))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.failedStep").value("ARRANGE_SHIPPING"))
				.andExpect(jsonPath("$.compensatedSteps", contains("PROCESS_PAYMENT", "RESERVE_INVENTORY")));
	}

	private String checkoutChoreography(SagaStep failAt) throws Exception {
		String responseBody = mockMvc
				.perform(post("/demo/saga/choreography/checkout").contentType("application/json")
						.content(objectMapper.writeValueAsString(sampleRequest(failAt))))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(responseBody).get("orderId").asText();
	}

	private CheckoutRequest sampleRequest(SagaStep failAt) {
		return new CheckoutRequest("customer-1", List.of(new OrderLine("p1", 2, new BigDecimal("9.99"))), failAt);
	}
}
