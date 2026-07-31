package com.testingai.webhooks.producer.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SubscriptionService {

	private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

	public Subscription register(String callbackUrl, String secret, Set<String> eventTypes) {
		String id = UUID.randomUUID().toString();
		Subscription subscription = new Subscription(id, callbackUrl, secret,
				Objects.requireNonNullElse(eventTypes, Set.of()));
		subscriptions.put(id, subscription);
		log.info("subscription {} registered for {} -> {}", id, eventTypes, callbackUrl);
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
		boolean removed = subscriptions.remove(id) != null;
		if (removed) {
			log.info("subscription {} removed", id);
		} else {
			log.warn("subscription {} not found for removal", id);
		}
		return removed;
	}
}
