package com.testingai.saga.orchestration;

import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

	private final InventoryParticipant inventoryParticipant;
	private final PaymentParticipant paymentParticipant;
	private final ShippingParticipant shippingParticipant;

	public SagaResult checkout(CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		List<SagaStep> completedSteps = new ArrayList<>();

		StepOutcome inventoryOutcome = inventoryParticipant.reserve(orderId, request.failAt());
		if (inventoryOutcome instanceof StepOutcome.Failure failure) {
			return new SagaResult(orderId, SagaStatus.CANCELLED, failure.step(), List.of());
		}
		completedSteps.add(SagaStep.RESERVE_INVENTORY);

		StepOutcome paymentOutcome = paymentParticipant.charge(orderId, request.failAt());
		if (paymentOutcome instanceof StepOutcome.Failure failure) {
			return compensate(orderId, failure, completedSteps);
		}
		completedSteps.add(SagaStep.PROCESS_PAYMENT);

		StepOutcome shippingOutcome = shippingParticipant.arrange(orderId, request.failAt());
		if (shippingOutcome instanceof StepOutcome.Failure failure) {
			return compensate(orderId, failure, completedSteps);
		}
		completedSteps.add(SagaStep.ARRANGE_SHIPPING);

		return new SagaResult(orderId, SagaStatus.CONFIRMED, null, List.of());
	}

	private SagaResult compensate(String orderId, StepOutcome.Failure failure, List<SagaStep> completedSteps) {
		List<SagaStep> compensatedSteps = new ArrayList<>();
		for (int i = completedSteps.size() - 1; i >= 0; i--) {
			SagaStep step = completedSteps.get(i);
			switch (step) {
				case RESERVE_INVENTORY -> inventoryParticipant.compensate(orderId);
				case PROCESS_PAYMENT -> paymentParticipant.compensate(orderId);
				case ARRANGE_SHIPPING -> shippingParticipant.compensate(orderId);
			}
			compensatedSteps.add(step);
		}
		return new SagaResult(orderId, SagaStatus.CANCELLED, failure.step(), compensatedSteps);
	}
}
