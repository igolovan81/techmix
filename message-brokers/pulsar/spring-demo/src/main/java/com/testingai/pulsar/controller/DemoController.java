package com.testingai.pulsar.controller;

import com.testingai.pulsar.pubsub.PubSubProducer;
import com.testingai.pulsar.routing.RoutingProducer;
import com.testingai.pulsar.simple.SimpleProducer;
import com.testingai.pulsar.transactions.TransactionalProducer;
import com.testingai.pulsar.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Tag(name = "Pulsar Demo", description = "Triggers for the five Apache Pulsar messaging patterns")
public class DemoController {

	private final SimpleProducer simpleProducer;
	private final WorkQueueProducer workQueueProducer;
	private final PubSubProducer pubSubProducer;
	private final RoutingProducer routingProducer;
	private final TransactionalProducer transactionalProducer;

	@PostMapping("/simple")
	@Operation(summary = "Publish to the simple topic (Exclusive subscription)")
	public ResponseEntity<String> simple(@Parameter(description = "Text to send") @RequestParam String message) {
		simpleProducer.send(message);
		return ResponseEntity.ok("Sent to simple-topic: " + message);
	}

	@PostMapping("/work")
	@Operation(summary = "Publish to the work topic (Shared subscription — A and B compete)")
	public ResponseEntity<String> work(
			@Parameter(description = "Text to send; dots simulate work duration (e.g. task..)") @RequestParam String message,
			@Parameter(description = "Number of messages (default 5)") @RequestParam(defaultValue = "5") int count) {
		workQueueProducer.send(message, count);
		return ResponseEntity.ok("Sent " + count + " messages to work-topic");
	}

	@PostMapping("/pubsub")
	@Operation(summary = "Publish to the pubsub topic (two independent Exclusive subscriptions)")
	public ResponseEntity<String> pubsub(@Parameter(description = "Text to broadcast") @RequestParam String message) {
		pubSubProducer.send(message);
		return ResponseEntity.ok("Broadcast to pubsub-topic: " + message);
	}

	@PostMapping("/routing")
	@Operation(summary = "Publish with a key (Key_Shared — same key always reaches the same consumer)")
	public ResponseEntity<String> routing(
			@Parameter(description = "Routing key — e.g. info, warning, error", schema = @Schema(allowableValues = {
					"info", "warning", "error"})) @RequestParam String key,
			@Parameter(description = "Text to route") @RequestParam String message) {
		routingProducer.send(key, message);
		return ResponseEntity.ok("Sent to routing-topic with key=" + key + ": " + message);
	}

	@PostMapping("/transaction")
	@Operation(summary = "Publish a batch (Pulsar transactions — requires transactionCoordinatorEnabled on broker)")
	public ResponseEntity<String> transaction(@Parameter(description = "Text to send") @RequestParam String message,
			@Parameter(description = "Batch size (default 3)") @RequestParam(defaultValue = "3") int count) {
		transactionalProducer.send(message, count);
		return ResponseEntity.ok("Sent " + count + " messages to tx-topic");
	}
}
