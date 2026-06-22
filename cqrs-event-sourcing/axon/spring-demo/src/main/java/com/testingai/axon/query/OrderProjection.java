package com.testingai.axon.query;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

	private final Map<String, OrderSummary> orders = new ConcurrentHashMap<>();

	@EventHandler
	public void on(OrderCreatedEvent event) {
		orders.put(event.orderId(), new OrderSummary(event.orderId(), event.customerId(), 0, "CREATED"));
	}

	@EventHandler
	public void on(OrderLineAddedEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount() + 1, summary.status()));
	}

	@EventHandler
	public void on(OrderConfirmedEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount(), "CONFIRMED"));
	}

	@EventHandler
	public void on(OrderCancelledEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount(), "CANCELLED"));
	}

	@ResetHandler
	public void onReset() {
		orders.clear();
	}

	@QueryHandler
	public OrderSummary handle(FindOrderQuery query) {
		return orders.get(query.orderId());
	}

	@QueryHandler
	public OrderSummaries handle(FindAllOrdersQuery query) {
		return new OrderSummaries(List.copyOf(orders.values()));
	}
}
