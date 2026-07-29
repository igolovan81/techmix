package com.testingai.camunda.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory read model of order status, updated by the job workers as the BPMN process executes and read back by
 * {@code DemoController}'s status endpoint. No persistence layer, matching {@code saga}/{@code template-engines}'
 * in-memory conventions.
 */
@Component
public class OrderReadModel {

	private final Map<String, Long> processInstanceKeyByOrderId = new ConcurrentHashMap<>();
	private final Map<String, OrderStatus> statusByOrderId = new ConcurrentHashMap<>();
	private final Map<String, List<OrderStep>> completedStepsByOrderId = new ConcurrentHashMap<>();

	public void register(String orderId, long processInstanceKey, OrderStatus status) {
		processInstanceKeyByOrderId.put(orderId, processInstanceKey);
		statusByOrderId.put(orderId, status);
		completedStepsByOrderId.put(orderId, new CopyOnWriteArrayList<>());
	}

	public void updateStatus(String orderId, OrderStatus status) {
		statusByOrderId.put(orderId, status);
	}

	public void recordStepCompleted(String orderId, OrderStep step) {
		completedStepsByOrderId.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>()).add(step);
	}

	public Optional<OrderView> find(String orderId) {
		OrderStatus status = statusByOrderId.get(orderId);
		if (status == null) {
			return Optional.empty();
		}
		Long processInstanceKey = processInstanceKeyByOrderId.get(orderId);
		List<OrderStep> steps = List.copyOf(completedStepsByOrderId.getOrDefault(orderId, List.of()));
		return Optional.of(new OrderView(orderId, processInstanceKey, status, steps));
	}
}
