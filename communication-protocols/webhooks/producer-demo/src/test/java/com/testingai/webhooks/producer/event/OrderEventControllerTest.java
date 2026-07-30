package com.testingai.webhooks.producer.event;

import com.testingai.webhooks.producer.delivery.WebhookDispatcher;
import com.testingai.webhooks.producer.subscription.Subscription;
import com.testingai.webhooks.producer.subscription.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderEventControllerTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();
	private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new OrderEventController(subscriptionService, webhookDispatcher)).build();

	@Test
	void triggerEvent_dispatchesOnlyToSubscriptionsMatchingEventType() throws Exception {
		Subscription matching = subscriptionService.register("http://a", "secret", Set.of("order.created"));
		Subscription nonMatching = subscriptionService.register("http://b", "secret", Set.of("order.paid"));
		when(webhookDispatcher.dispatch(eq(matching), any(OrderEvent.class))).thenReturn("delivery-1");

		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-1", "created"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$[0]").value("delivery-1"));

		verify(webhookDispatcher, times(1)).dispatch(eq(matching), any(OrderEvent.class));
		verify(webhookDispatcher, never()).dispatch(eq(nonMatching), any());
	}

	@Test
	void triggerEvent_prefixesEventTypeWithOrderDot_andSetsOrderId() throws Exception {
		Subscription subscription = subscriptionService.register("http://a", "secret", Set.of("order.shipped"));
		when(webhookDispatcher.dispatch(any(), any())).thenReturn("delivery-2");

		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-2", "shipped"))
				.andExpect(status().isAccepted());

		ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
		verify(webhookDispatcher).dispatch(eq(subscription), captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo("order.shipped");
		assertThat(captor.getValue().orderId()).isEqualTo("order-2");
	}

	@Test
	void triggerEvent_returnsEmptyList_whenNoSubscriptionsMatch() throws Exception {
		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-3", "cancelled"))
				.andExpect(status().isAccepted()).andExpect(content().json("[]"));
	}
}
