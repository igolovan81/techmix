package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private PaymentParticipant paymentParticipant;

	@BeforeEach
	void setUp() {
		paymentParticipant = new PaymentParticipant(publisher, new SagaLog());
	}

	@Test
	void onInventoryReserved_shouldChargeAndPublishPaymentProcessedWhenNotToldToFail() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", null));

		assertThat(paymentParticipant.hasCharge("order-1")).isTrue();
		verify(publisher).publishEvent(new PaymentProcessed("order-1", null));
	}

	@Test
	void onInventoryReserved_shouldPublishPaymentFailedWhenToldToFail() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", SagaStep.PROCESS_PAYMENT));

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
		verify(publisher).publishEvent(new PaymentFailed("order-1", "card declined (simulated)"));
	}

	@Test
	void onShipmentFailed_shouldRefundAndPublishPaymentRefunded() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", SagaStep.ARRANGE_SHIPPING));

		paymentParticipant.onShipmentFailed(new ShipmentFailed("order-1", "carrier unavailable (simulated)"));

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
		verify(publisher).publishEvent(new PaymentRefunded("order-1"));
	}
}
