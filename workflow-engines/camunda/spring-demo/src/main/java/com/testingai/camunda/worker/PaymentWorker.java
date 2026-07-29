package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "process-payment")
	public void processPayment(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		String failAt = (String) job.getVariablesAsMap().get("failAt");
		log.info("[process-payment] orderId={}", orderId);
		if (OrderStep.PROCESS_PAYMENT.name().equals(failAt)) {
			log.warn("[process-payment] simulated failure for orderId={}", orderId);
			throw new BpmnError("PAYMENT_DECLINED", "Card declined for order " + orderId);
		}
		orderReadModel.recordStepCompleted(orderId, OrderStep.PROCESS_PAYMENT);
	}
}
