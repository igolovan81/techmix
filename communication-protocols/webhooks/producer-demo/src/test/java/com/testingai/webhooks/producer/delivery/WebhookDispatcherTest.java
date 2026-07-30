package com.testingai.webhooks.producer.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.webhooks.producer.event.OrderEvent;
import com.testingai.webhooks.producer.security.HmacSigner;
import com.testingai.webhooks.producer.subscription.Subscription;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebhookDispatcherTest {

	private MockWebServer server;
	private HmacSigner hmacSigner;
	private WebhookDispatcher dispatcher;

	@BeforeEach
	void startServer() throws IOException {
		server = new MockWebServer();
		server.start();
		hmacSigner = new HmacSigner();
	}

	@AfterEach
	void stopServer() throws IOException {
		server.shutdown();
	}

	private WebhookDispatcher dispatcherWithSchedule(List<Duration> backoff) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(2));
		RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
		SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		return new WebhookDispatcher(restClient, taskScheduler, hmacSigner, objectMapper,
				new RetryBackoffSchedule(backoff));
	}

	private Subscription subscriptionFor(String secret) {
		return new Subscription("sub-1", server.url("/webhooks/orders").toString(), secret, Set.of("order.created"));
	}

	@Test
	void dispatch_succeedsOnFirstAttempt_andSignsPayloadWithSubscriptionSecret() throws InterruptedException {
		server.enqueue(new MockResponse().setResponseCode(200));
		dispatcher = dispatcherWithSchedule(List.of(Duration.ofMillis(50)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-1", Instant.parse("2026-01-01T00:00:00Z"), Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS).until(() -> dispatcher.deliveries().stream().anyMatch(
				attempt -> attempt.deliveryId().equals(deliveryId) && attempt.status() == DeliveryStatus.SUCCEEDED));

		RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recorded).isNotNull();
		assertThat(recorded.getPath()).isEqualTo("/webhooks/orders");
		assertThat(recorded.getHeader("X-Webhook-Id")).isEqualTo(deliveryId);
		assertThat(recorded.getHeader("X-Webhook-Event")).isEqualTo("order.created");
		String expectedSignature = "sha256=" + hmacSigner.sign("test-secret", recorded.getBody().readUtf8());
		assertThat(recorded.getHeader("X-Webhook-Signature")).isEqualTo(expectedSignature);
	}

	@Test
	void dispatch_retriesWithBackoff_thenSucceeds() {
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(200));
		dispatcher = dispatcherWithSchedule(
				List.of(Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofMillis(50)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-2", Instant.parse("2026-01-01T00:00:00Z"), Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS).until(() -> dispatcher.deliveries().stream().anyMatch(
				attempt -> attempt.deliveryId().equals(deliveryId) && attempt.status() == DeliveryStatus.SUCCEEDED));

		DeliveryAttempt attempt = dispatcher.deliveries().stream()
				.filter(candidate -> candidate.deliveryId().equals(deliveryId)).findFirst().orElseThrow();
		assertThat(attempt.attemptCount()).isEqualTo(3);
		assertThat(server.getRequestCount()).isEqualTo(3);
	}

	@Test
	void dispatch_deadLetters_afterExhaustingAllAttempts() {
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(500));
		dispatcher = dispatcherWithSchedule(List.of(Duration.ofMillis(30), Duration.ofMillis(30)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-3", Instant.parse("2026-01-01T00:00:00Z"), Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS).until(
				() -> dispatcher.deliveries().stream().anyMatch(attempt -> attempt.deliveryId().equals(deliveryId)
						&& attempt.status() == DeliveryStatus.DEAD_LETTERED));

		assertThat(dispatcher.deadLetters()).extracting(DeliveryAttempt::deliveryId).containsExactly(deliveryId);
		assertThat(server.getRequestCount()).isEqualTo(2);
	}
}
