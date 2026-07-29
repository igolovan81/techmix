package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "arrange-shipping")
	public void arrangeShipping(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		log.info("[arrange-shipping] orderId={}", orderId);
		orderReadModel.recordStepCompleted(orderId, OrderStep.ARRANGE_SHIPPING);
		orderReadModel.updateStatus(orderId, OrderStatus.FULFILLED);
	}
}
