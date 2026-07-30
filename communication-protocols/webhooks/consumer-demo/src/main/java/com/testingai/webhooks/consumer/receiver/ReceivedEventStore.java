package com.testingai.webhooks.consumer.receiver;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ReceivedEventStore {

	private final Set<String> seenDeliveryIds = ConcurrentHashMap.newKeySet();
	private final List<ReceivedEvent> events = new CopyOnWriteArrayList<>();

	public boolean recordIfNew(String deliveryId, String eventType, String orderId) {
		boolean isNew = seenDeliveryIds.add(deliveryId);
		events.add(new ReceivedEvent(deliveryId, eventType, orderId, Instant.now(), !isNew));
		return isNew;
	}

	public List<ReceivedEvent> all() {
		return List.copyOf(events);
	}
}
