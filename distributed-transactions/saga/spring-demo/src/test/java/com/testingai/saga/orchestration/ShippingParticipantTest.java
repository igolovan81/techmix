package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingParticipantTest {

	private final ShippingParticipant shippingParticipant = new ShippingParticipant();

	@Test
	void arrange_shouldSucceedAndRecordArrangementWhenNotToldToFail() {
		StepOutcome outcome = shippingParticipant.arrange("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING));
		assertThat(shippingParticipant.hasArrangement("order-1")).isTrue();
	}

	@Test
	void arrange_shouldFailAndRecordNoArrangementWhenToldToFail() {
		StepOutcome outcome = shippingParticipant.arrange("order-1", SagaStep.ARRANGE_SHIPPING);

		assertThat(outcome)
				.isEqualTo(new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)"));
		assertThat(shippingParticipant.hasArrangement("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveArrangement() {
		shippingParticipant.arrange("order-1", null);

		shippingParticipant.compensate("order-1");

		assertThat(shippingParticipant.hasArrangement("order-1")).isFalse();
	}
}
