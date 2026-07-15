package com.testingai.saga.controller;

import com.testingai.saga.choreography.OrderParticipant;
import com.testingai.saga.choreography.SagaLog;
import com.testingai.saga.choreography.SagaLogEntry;
import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.orchestration.SagaOrchestrator;
import com.testingai.saga.orchestration.SagaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo/saga")
@RequiredArgsConstructor
public class DemoController {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final OrderParticipant orderParticipant;
	private final SagaOrchestrator orchestrator;

	@PostMapping("/choreography/checkout")
	public ResponseEntity<CheckoutResponse> choreographyCheckout(@RequestBody CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		publisher.publishEvent(
				new CheckoutRequested(orderId, request.customerId(), request.items(), request.failAt()));
		return ResponseEntity.accepted().body(new CheckoutResponse(orderId));
	}

	@GetMapping("/choreography/orders/{orderId}")
	public ResponseEntity<ChoreographyOrderView> choreographyOrder(@PathVariable String orderId) {
		List<SagaLogEntry> timeline = sagaLog.timelineFor(orderId);
		if (timeline.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		SagaStatus status = orderParticipant.statusOf(orderId);
		return ResponseEntity.ok(new ChoreographyOrderView(orderId, status, timeline));
	}

	@PostMapping("/orchestration/checkout")
	public ResponseEntity<SagaResult> orchestrationCheckout(@RequestBody CheckoutRequest request) {
		return ResponseEntity.ok(orchestrator.checkout(request));
	}
}
