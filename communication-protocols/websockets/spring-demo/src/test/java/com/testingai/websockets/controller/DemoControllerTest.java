package com.testingai.websockets.controller;

import com.testingai.websockets.domain.OrderTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoControllerTest {

	private final OrderTrackingService orderTrackingService = new OrderTrackingService(List.of());
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(orderTrackingService)).build();

	@Test
	void createOrder_returnsNewOrderInCreatedStatus() throws Exception {
		mockMvc.perform(post("/api/orders")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREATED"));
	}

	@Test
	void advanceOrder_movesToNextStatus() throws Exception {
		String orderId = orderTrackingService.create().id();

		mockMvc.perform(post("/api/orders/{id}/advance", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
	}

	@Test
	void advanceOrder_returns409_whenOrderIsInTerminalStatus() throws Exception {
		String orderId = orderTrackingService.create().id();
		orderTrackingService.advance(orderId);
		orderTrackingService.advance(orderId);
		orderTrackingService.advance(orderId);

		mockMvc.perform(post("/api/orders/{id}/advance", orderId)).andExpect(status().isConflict());
	}

	@Test
	void advanceOrder_returns404_whenOrderUnknown() throws Exception {
		mockMvc.perform(post("/api/orders/{id}/advance", "unknown")).andExpect(status().isNotFound());
	}
}
