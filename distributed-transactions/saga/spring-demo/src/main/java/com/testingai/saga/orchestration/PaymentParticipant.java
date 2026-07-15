package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component("orchestrationPaymentParticipant")
public class PaymentParticipant {

	private final Set<String> chargedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome charge(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.PROCESS_PAYMENT) {
			return new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)");
		}
		chargedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.PROCESS_PAYMENT);
	}

	public void compensate(String orderId) {
		chargedOrderIds.remove(orderId);
	}

	public boolean hasCharge(String orderId) {
		return chargedOrderIds.contains(orderId);
	}
}
