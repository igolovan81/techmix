package com.testingai.webhooks.consumer.admin;

import com.testingai.webhooks.consumer.receiver.ReceivedEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

	private final ReceivedEventStore receivedEventStore = new ReceivedEventStore();
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(receivedEventStore)).build();

	@Test
	void received_returnsAllRecordedEvents() throws Exception {
		receivedEventStore.recordIfNew("d1", "order.created", "order-1");

		mockMvc.perform(get("/admin/received")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d1")).andExpect(jsonPath("$[0].orderId").value("order-1"))
				.andExpect(jsonPath("$[0].duplicate").value(false));
	}
}
