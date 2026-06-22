package com.testingai.axon.command;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import com.testingai.axon.util.FailureSimulator;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate(snapshotTriggerDefinition = "orderSnapshotTriggerDefinition")
public class OrderAggregate {

	@AggregateIdentifier
	private String orderId;
	private boolean confirmed;

	@CommandHandler
	public OrderAggregate(CreateOrderCommand command) {
		apply(new OrderCreatedEvent(command.orderId(), command.customerId()));
	}

	@CommandHandler
	public void handle(AddOrderLineCommand command) {
		apply(new OrderLineAddedEvent(command.orderId(), command.productId(), command.quantity(), command.price()));
	}

	@CommandHandler
	public void handle(ConfirmOrderCommand command) {
		if (confirmed) {
			throw new IllegalStateException("Order " + command.orderId() + " is already confirmed");
		}
		FailureSimulator.maybeThrow("confirm-order");
		apply(new OrderConfirmedEvent(command.orderId()));
	}

	@CommandHandler
	public void handle(CancelOrderCommand command) {
		if (confirmed) {
			throw new IllegalStateException("Cannot cancel order " + command.orderId() + " after it is confirmed");
		}
		apply(new OrderCancelledEvent(command.orderId()));
	}

	@EventSourcingHandler
	public void on(OrderCreatedEvent event) {
		this.orderId = event.orderId();
	}

	@EventSourcingHandler
	public void on(OrderConfirmedEvent event) {
		this.confirmed = true;
	}

	protected OrderAggregate() {
	}
}
