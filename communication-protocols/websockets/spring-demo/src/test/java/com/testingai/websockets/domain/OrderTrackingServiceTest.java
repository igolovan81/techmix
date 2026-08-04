package com.testingai.websockets.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTrackingServiceTest {

	private final List<OrderEvent> published = new ArrayList<>();
	private final OrderTrackingService service = new OrderTrackingService(List.of(published::add));

	@Test
	void create_returnsOrderInCreatedStatus() {
		Order order = service.create();

		assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void advance_movesThroughFullStatusSequence() {
		Order order = service.create();

		Order paid = service.advance(order.id());
		Order shipped = service.advance(order.id());
		Order delivered = service.advance(order.id());

		assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
		assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
	}

	@Test
	void advance_publishesEventToEveryPublisher_onEachTransition() {
		Order order = service.create();

		service.advance(order.id());

		assertThat(published).hasSize(1);
		assertThat(published.get(0).orderId()).isEqualTo(order.id());
		assertThat(published.get(0).status()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void advance_throwsNoNextStatusException_whenOrderAlreadyDelivered() {
		Order order = service.create();
		service.advance(order.id());
		service.advance(order.id());
		service.advance(order.id());

		assertThatThrownBy(() -> service.advance(order.id())).isInstanceOf(NoNextStatusException.class);
	}

	@Test
	void advance_throwsOrderNotFoundException_whenOrderUnknown() {
		assertThatThrownBy(() -> service.advance("unknown")).isInstanceOf(OrderNotFoundException.class);
	}

	@Test
	void get_returnsCurrentOrderState() {
		Order order = service.create();

		assertThat(service.get(order.id())).isEqualTo(order);
	}

	@Test
	void get_throwsOrderNotFoundException_whenOrderUnknown() {
		assertThatThrownBy(() -> service.get("unknown")).isInstanceOf(OrderNotFoundException.class);
	}
}
