package com.testingai.websockets.controller;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

	private final OrderTrackingService orderTrackingService;

	public DemoController(OrderTrackingService orderTrackingService) {
		this.orderTrackingService = orderTrackingService;
	}

	@PostMapping("/api/orders")
	public ResponseEntity<Order> createOrder() {
		return ResponseEntity.ok(orderTrackingService.create());
	}

	@PostMapping("/api/orders/{id}/advance")
	public ResponseEntity<Order> advanceOrder(@PathVariable String id) {
		return ResponseEntity.ok(orderTrackingService.advance(id));
	}
}
