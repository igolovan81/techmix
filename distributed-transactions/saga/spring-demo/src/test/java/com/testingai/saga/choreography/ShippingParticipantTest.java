package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShippingParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private ShippingParticipant shippingParticipant;

	@BeforeEach
	void setUp() {
		shippingParticipant = new ShippingParticipant(publisher, new SagaLog());
	}

	@Test
	void onPaymentProcessed_shouldPublishShipmentArrangedWhenNotToldToFail() {
		shippingParticipant.onPaymentProcessed(new PaymentProcessed("order-1", null));

		verify(publisher).publishEvent(new ShipmentArranged("order-1"));
	}

	@Test
	void onPaymentProcessed_shouldPublishShipmentFailedWhenToldToFail() {
		shippingParticipant.onPaymentProcessed(new PaymentProcessed("order-1", SagaStep.ARRANGE_SHIPPING));

		verify(publisher).publishEvent(new ShipmentFailed("order-1", "carrier unavailable (simulated)"));
	}
}
