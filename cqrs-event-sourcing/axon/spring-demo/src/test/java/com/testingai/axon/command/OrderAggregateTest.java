package com.testingai.axon.command;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import com.testingai.axon.util.FailureSimulator;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class OrderAggregateTest {

	private static final String ORDER_ID = "order-1";
	private static final String CUSTOMER_ID = "customer-1";

	private FixtureConfiguration<OrderAggregate> fixture;

	@BeforeEach
	void setUp() {
		fixture = new AggregateTestFixture<>(OrderAggregate.class);
	}

	@Test
	void create_shouldEmitOrderCreatedEvent() {
		fixture.givenNoPriorActivity().when(new CreateOrderCommand(ORDER_ID, CUSTOMER_ID))
				.expectEvents(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
	}

	@Test
	void addLine_shouldEmitOrderLineAddedEvent() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID))
				.when(new AddOrderLineCommand(ORDER_ID, "product-1", 2, BigDecimal.TEN))
				.expectEvents(new OrderLineAddedEvent(ORDER_ID, "product-1", 2, BigDecimal.TEN));
	}

	@Test
	void confirm_shouldEmitOrderConfirmedEvent() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new ConfirmOrderCommand(ORDER_ID))
					.expectEvents(new OrderConfirmedEvent(ORDER_ID));
		}
	}

	@Test
	void confirm_whenAlreadyConfirmed_shouldRejectAndEmitNoEvents() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID), new OrderConfirmedEvent(ORDER_ID))
					.when(new ConfirmOrderCommand(ORDER_ID)).expectException(IllegalStateException.class)
					.expectNoEvents();
		}
	}

	@Test
	void confirm_whenSimulatedFailure_shouldRejectAndEmitNoEvents() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString()))
					.thenThrow(new RuntimeException("Simulated 5% failure in confirm-order"));

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new ConfirmOrderCommand(ORDER_ID))
					.expectException(RuntimeException.class).expectNoEvents();
		}
	}

	@Test
	void cancel_shouldEmitOrderCancelledEvent() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new CancelOrderCommand(ORDER_ID))
				.expectEvents(new OrderCancelledEvent(ORDER_ID));
	}

	@Test
	void cancel_whenAlreadyConfirmed_shouldRejectAndEmitNoEvents() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID), new OrderConfirmedEvent(ORDER_ID))
				.when(new CancelOrderCommand(ORDER_ID)).expectException(IllegalStateException.class).expectNoEvents();
	}
}
