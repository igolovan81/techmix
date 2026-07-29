package com.testingai.camunda.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReadModelTest {

	private final OrderReadModel readModel = new OrderReadModel();

	@Test
	void find_returnsEmpty_whenOrderUnknown() {
		assertThat(readModel.find("unknown")).isEmpty();
	}

	@Test
	void register_makesOrderFindable() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		OrderView view = readModel.find("o1").orElseThrow();
		assertThat(view.orderId()).isEqualTo("o1");
		assertThat(view.processInstanceKey()).isEqualTo(123L);
		assertThat(view.status()).isEqualTo(OrderStatus.IN_PROGRESS);
		assertThat(view.completedSteps()).isEmpty();
	}

	@Test
	void updateStatus_changesStatus() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		readModel.updateStatus("o1", OrderStatus.CANCELLED);

		assertThat(readModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void recordStepCompleted_appendsSteps_inOrder() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		readModel.recordStepCompleted("o1", OrderStep.RESERVE_INVENTORY);
		readModel.recordStepCompleted("o1", OrderStep.PROCESS_PAYMENT);

		assertThat(readModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.RESERVE_INVENTORY,
				OrderStep.PROCESS_PAYMENT);
	}
}
