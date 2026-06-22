package com.testingai.axon.controller;

import com.testingai.axon.command.AddOrderLineCommand;
import com.testingai.axon.command.CancelOrderCommand;
import com.testingai.axon.command.ConfirmOrderCommand;
import com.testingai.axon.command.CreateOrderCommand;
import com.testingai.axon.query.FindAllOrdersQuery;
import com.testingai.axon.query.FindOrderQuery;
import com.testingai.axon.query.OrderSummaries;
import com.testingai.axon.query.OrderSummary;
import com.testingai.axon.replay.ReplayService;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo/orders")
@RequiredArgsConstructor
public class DemoController {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;
	private final ReplayService replayService;

	@PostMapping
	public String createOrder(@RequestBody CreateOrderRequest request) {
		String orderId = UUID.randomUUID().toString();
		commandGateway.sendAndWait(new CreateOrderCommand(orderId, request.customerId()));
		return orderId;
	}

	@PostMapping("/{orderId}/lines")
	public void addLine(@PathVariable String orderId, @RequestBody AddOrderLineRequest request) {
		commandGateway.sendAndWait(
				new AddOrderLineCommand(orderId, request.productId(), request.quantity(), request.price()));
	}

	@PostMapping("/{orderId}/confirm")
	public void confirmOrder(@PathVariable String orderId) {
		commandGateway.sendAndWait(new ConfirmOrderCommand(orderId));
	}

	@PostMapping("/{orderId}/cancel")
	public void cancelOrder(@PathVariable String orderId) {
		commandGateway.sendAndWait(new CancelOrderCommand(orderId));
	}

	@GetMapping("/{orderId}")
	public OrderSummary getOrder(@PathVariable String orderId) {
		OrderSummary summary = queryGateway.query(new FindOrderQuery(orderId), OrderSummary.class).join();
		if (summary == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found");
		}
		return summary;
	}

	@GetMapping
	public List<OrderSummary> getAllOrders() {
		return queryGateway.query(new FindAllOrdersQuery(), OrderSummaries.class).join().orders();
	}

	@PostMapping("/replay")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void replay() {
		replayService.replayOrderProjection();
	}
}
