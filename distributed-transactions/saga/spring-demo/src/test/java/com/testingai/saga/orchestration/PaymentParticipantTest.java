package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentParticipantTest {

	private final PaymentParticipant paymentParticipant = new PaymentParticipant();

	@Test
	void charge_shouldSucceedAndRecordChargeWhenNotToldToFail() {
		StepOutcome outcome = paymentParticipant.charge("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		assertThat(paymentParticipant.hasCharge("order-1")).isTrue();
	}

	@Test
	void charge_shouldFailAndRecordNoChargeWhenToldToFail() {
		StepOutcome outcome = paymentParticipant.charge("order-1", SagaStep.PROCESS_PAYMENT);

		assertThat(outcome).isEqualTo(new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)"));
		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveCharge() {
		paymentParticipant.charge("order-1", null);

		paymentParticipant.compensate("order-1");

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
	}
}
