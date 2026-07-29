package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final InventoryWorker worker = new InventoryWorker(orderReadModel);

	@Test
	void reserveInventory_marksReserved_whenNoFailAt() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		Map<String, Object> result = worker.reserveInventory(job);

		assertThat(result).containsEntry("inventoryReserved", true);
		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps())
				.containsExactly(OrderStep.RESERVE_INVENTORY);
	}

	@Test
	void reserveInventory_throwsInventoryUnavailable_whenFailAtMatches() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1", "failAt", "RESERVE_INVENTORY"));

		assertThatThrownBy(() -> worker.reserveInventory(job)).isInstanceOf(BpmnError.class)
				.satisfies(ex -> assertThat(((BpmnError) ex).getErrorCode()).isEqualTo("INVENTORY_UNAVAILABLE"));
		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void releaseInventory_marksOrderCancelled() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		Map<String, Object> result = worker.releaseInventory(job);

		assertThat(result).containsEntry("inventoryReserved", false);
		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}
}
