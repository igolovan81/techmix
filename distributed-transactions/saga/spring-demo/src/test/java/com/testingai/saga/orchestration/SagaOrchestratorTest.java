package com.testingai.saga.orchestration;

import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

	@Mock
	private InventoryParticipant inventoryParticipant;
	@Mock
	private PaymentParticipant paymentParticipant;
	@Mock
	private ShippingParticipant shippingParticipant;

	private SagaOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		orchestrator = new SagaOrchestrator(inventoryParticipant, paymentParticipant, shippingParticipant);
	}

	@Test
	void checkout_shouldConfirmOrderWhenAllStepsSucceed() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		when(shippingParticipant.arrange(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING));

		SagaResult result = orchestrator.checkout(new CheckoutRequest("customer-1", List.of(), null));

		assertThat(result.status()).isEqualTo(SagaStatus.CONFIRMED);
		assertThat(result.failedStep()).isNull();
		assertThat(result.compensatedSteps()).isEmpty();
		InOrder order = inOrder(inventoryParticipant, paymentParticipant, shippingParticipant);
		order.verify(inventoryParticipant).reserve(anyString(), any());
		order.verify(paymentParticipant).charge(anyString(), any());
		order.verify(shippingParticipant).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCancelWithoutCompensationWhenInventoryFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.RESERVE_INVENTORY));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
		assertThat(result.compensatedSteps()).isEmpty();
		verify(paymentParticipant, never()).charge(anyString(), any());
		verify(shippingParticipant, never()).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCompensateInventoryWhenPaymentFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.PROCESS_PAYMENT));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
		assertThat(result.compensatedSteps()).containsExactly(SagaStep.RESERVE_INVENTORY);
		verify(inventoryParticipant).compensate(anyString());
		verify(shippingParticipant, never()).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCompensatePaymentThenInventoryWhenShippingFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		when(shippingParticipant.arrange(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.ARRANGE_SHIPPING));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.ARRANGE_SHIPPING);
		assertThat(result.compensatedSteps()).containsExactly(SagaStep.PROCESS_PAYMENT, SagaStep.RESERVE_INVENTORY);
		InOrder order = inOrder(paymentParticipant, inventoryParticipant);
		order.verify(paymentParticipant).compensate(anyString());
		order.verify(inventoryParticipant).compensate(anyString());
	}
}
