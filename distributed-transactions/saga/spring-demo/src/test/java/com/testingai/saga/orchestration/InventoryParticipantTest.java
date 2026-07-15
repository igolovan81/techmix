package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryParticipantTest {

	private final InventoryParticipant inventoryParticipant = new InventoryParticipant();

	@Test
	void reserve_shouldSucceedAndRecordReservationWhenNotToldToFail() {
		StepOutcome outcome = inventoryParticipant.reserve("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		assertThat(inventoryParticipant.hasReservation("order-1")).isTrue();
	}

	@Test
	void reserve_shouldFailAndRecordNoReservationWhenToldToFail() {
		StepOutcome outcome = inventoryParticipant.reserve("order-1", SagaStep.RESERVE_INVENTORY);

		assertThat(outcome)
				.isEqualTo(new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)"));
		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveReservation() {
		inventoryParticipant.reserve("order-1", null);

		inventoryParticipant.compensate("order-1");

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
	}
}
