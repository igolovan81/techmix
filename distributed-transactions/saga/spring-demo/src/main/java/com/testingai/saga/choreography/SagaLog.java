package com.testingai.saga.choreography;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SagaLog {

	private final Map<String, List<SagaLogEntry>> entriesByOrderId = new ConcurrentHashMap<>();

	public void append(String orderId, String step, SagaLogEntry.Outcome outcome, String detail) {
		entriesByOrderId.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>())
				.add(new SagaLogEntry(step, outcome, detail, Instant.now()));
	}

	public List<SagaLogEntry> timelineFor(String orderId) {
		return entriesByOrderId.getOrDefault(orderId, List.of());
	}
}
