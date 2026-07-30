package com.testingai.webhooks.producer.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptionService))
			.build();

	@BeforeEach
	void resetState() {
		subscriptionService.findAll().forEach(subscription -> subscriptionService.remove(subscription.id()));
	}

	@Test
	void register_returns201_andEchoesCallbackUrlAndEventTypes() throws Exception {
		mockMvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON).content("""
				{"callbackUrl":"http://localhost:8097/webhooks/orders","secret":"s3cret","eventTypes":["order.created"]}
				""")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.callbackUrl").value("http://localhost:8097/webhooks/orders"))
				.andExpect(jsonPath("$.eventTypes", hasSize(1)));
	}

	@Test
	void list_returnsAllRegisteredSubscriptions() throws Exception {
		subscriptionService.register("http://a", "secret", java.util.Set.of("order.created"));
		subscriptionService.register("http://b", "secret", java.util.Set.of("order.paid"));

		mockMvc.perform(get("/subscriptions")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void delete_returns204_whenSubscriptionExists() throws Exception {
		Subscription subscription = subscriptionService.register("http://a", "secret",
				java.util.Set.of("order.created"));

		mockMvc.perform(delete("/subscriptions/{id}", subscription.id())).andExpect(status().isNoContent());
	}

	@Test
	void delete_returns404_whenSubscriptionUnknown() throws Exception {
		mockMvc.perform(delete("/subscriptions/{id}", "unknown-id")).andExpect(status().isNotFound());
	}
}
