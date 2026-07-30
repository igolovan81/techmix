package com.testingai.webhooks.producer.subscription;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionServiceTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();

	@Test
	void register_assignsIdAndStoresSubscription() {
		Subscription subscription = subscriptionService.register("http://localhost:8097/webhooks/orders", "secret",
				Set.of("order.created"));

		assertThat(subscription.id()).isNotBlank();
		assertThat(subscriptionService.findAll()).containsExactly(subscription);
	}

	@Test
	void register_defaultsToEmptyEventTypes_whenNullPassed() {
		Subscription subscription = subscriptionService.register("http://localhost:8097/webhooks/orders", "secret",
				null);

		assertThat(subscription.eventTypes()).isEmpty();
	}

	@Test
	void findByEventType_returnsOnlyMatchingSubscriptions() {
		subscriptionService.register("http://a", "secret", Set.of("order.created"));
		Subscription paidSubscription = subscriptionService.register("http://b", "secret", Set.of("order.paid"));

		assertThat(subscriptionService.findByEventType("order.paid")).containsExactly(paidSubscription);
	}

	@Test
	void remove_deletesSubscription_andReturnsTrue() {
		Subscription subscription = subscriptionService.register("http://a", "secret", Set.of("order.created"));

		boolean removed = subscriptionService.remove(subscription.id());

		assertThat(removed).isTrue();
		assertThat(subscriptionService.findAll()).isEmpty();
	}

	@Test
	void remove_returnsFalse_whenIdUnknown() {
		assertThat(subscriptionService.remove("unknown-id")).isFalse();
	}
}
