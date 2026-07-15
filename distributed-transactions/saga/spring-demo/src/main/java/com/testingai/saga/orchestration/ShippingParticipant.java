package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShippingParticipant {

	private final Set<String> arrangedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome arrange(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.ARRANGE_SHIPPING) {
			return new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)");
		}
		arrangedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING);
	}

	public void compensate(String orderId) {
		arrangedOrderIds.remove(orderId);
	}

	public boolean hasArrangement(String orderId) {
		return arrangedOrderIds.contains(orderId);
	}
}
