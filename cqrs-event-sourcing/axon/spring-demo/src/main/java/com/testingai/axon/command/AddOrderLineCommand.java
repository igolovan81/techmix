package com.testingai.axon.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

public record AddOrderLineCommand(@TargetAggregateIdentifier String orderId, String productId, int quantity,
		BigDecimal price) {
}
