package com.testingai.webhooks.producer.subscription;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionService {

	private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

	public Subscription register(String callbackUrl, String secret, Set<String> eventTypes) {
		String id = UUID.randomUUID().toString();
		Subscription subscription = new Subscription(id, callbackUrl, secret,
				Objects.requireNonNullElse(eventTypes, Set.of()));
		subscriptions.put(id, subscription);
		return subscription;
	}

	public List<Subscription> findAll() {
		return List.copyOf(subscriptions.values());
	}

	public List<Subscription> findByEventType(String eventType) {
		return subscriptions.values().stream().filter(subscription -> subscription.eventTypes().contains(eventType))
				.toList();
	}

	public boolean remove(String id) {
		return subscriptions.remove(id) != null;
	}
}
