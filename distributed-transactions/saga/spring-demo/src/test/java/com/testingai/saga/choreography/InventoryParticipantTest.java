package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private InventoryParticipant inventoryParticipant;

	@BeforeEach
	void setUp() {
		inventoryParticipant = new InventoryParticipant(publisher, new SagaLog());
	}

	@Test
	void onOrderCreated_shouldReserveAndPublishInventoryReservedWhenNotToldToFail() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), null));

		assertThat(inventoryParticipant.hasReservation("order-1")).isTrue();
		verify(publisher).publishEvent(new InventoryReserved("order-1", null));
	}

	@Test
	void onOrderCreated_shouldPublishInventoryReservationFailedWhenToldToFail() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.RESERVE_INVENTORY));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReservationFailed("order-1", "insufficient stock (simulated)"));
	}

	@Test
	void onPaymentFailed_shouldReleaseReservationAndPublishInventoryReleased() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.PROCESS_PAYMENT));

		inventoryParticipant.onPaymentFailed(new PaymentFailed("order-1", "card declined (simulated)"));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReleased("order-1"));
	}

	@Test
	void onPaymentRefunded_shouldReleaseReservationAndPublishInventoryReleased() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.ARRANGE_SHIPPING));

		inventoryParticipant.onPaymentRefunded(new PaymentRefunded("order-1"));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReleased("order-1"));
	}
}
