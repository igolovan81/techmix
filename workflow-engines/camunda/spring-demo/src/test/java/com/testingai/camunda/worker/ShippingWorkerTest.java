package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final ShippingWorker worker = new ShippingWorker(orderReadModel);

	@Test
	void arrangeShipping_marksOrderFulfilled() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		worker.arrangeShipping(job);

		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.FULFILLED);
		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps())
				.containsExactly(OrderStep.ARRANGE_SHIPPING);
	}
}
