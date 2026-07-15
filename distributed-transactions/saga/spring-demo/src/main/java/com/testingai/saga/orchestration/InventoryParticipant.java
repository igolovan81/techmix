package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component("orchestrationInventoryParticipant")
public class InventoryParticipant {

	private final Set<String> reservedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome reserve(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.RESERVE_INVENTORY) {
			return new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)");
		}
		reservedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.RESERVE_INVENTORY);
	}

	public void compensate(String orderId) {
		reservedOrderIds.remove(orderId);
	}

	public boolean hasReservation(String orderId) {
		return reservedOrderIds.contains(orderId);
	}
}
