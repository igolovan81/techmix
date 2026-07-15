package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCancelled;
import com.testingai.saga.choreography.event.OrderConfirmed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.domain.SagaStatus;
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
class OrderParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private OrderParticipant orderParticipant;

	@BeforeEach
	void setUp() {
		orderParticipant = new OrderParticipant(publisher, new SagaLog());
	}

	@Test
	void onCheckoutRequested_shouldCreateOrderAndPublishOrderCreated() {
		orderParticipant.onCheckoutRequested(new CheckoutRequested("order-1", "customer-1", List.of(), null));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.PENDING);
		verify(publisher).publishEvent(new OrderCreated("order-1", List.of(), null));
	}

	@Test
	void onShipmentArranged_shouldConfirmOrderAndPublishOrderConfirmed() {
		orderParticipant.onShipmentArranged(new ShipmentArranged("order-1"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CONFIRMED);
		verify(publisher).publishEvent(new OrderConfirmed("order-1"));
	}

	@Test
	void onInventoryReservationFailed_shouldCancelOrderDirectly() {
		orderParticipant.onInventoryReservationFailed(new InventoryReservationFailed("order-1", "out of stock"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CANCELLED);
		verify(publisher).publishEvent(new OrderCancelled("order-1", "out of stock"));
	}

	@Test
	void onInventoryReleased_shouldCancelOrderAfterCompensation() {
		orderParticipant.onInventoryReleased(new InventoryReleased("order-1"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CANCELLED);
		verify(publisher).publishEvent(new OrderCancelled("order-1", "compensated after a downstream step failed"));
	}
}
