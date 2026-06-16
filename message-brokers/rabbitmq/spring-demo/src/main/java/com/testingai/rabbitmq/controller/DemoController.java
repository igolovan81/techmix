package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
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
@Tag(name = "RabbitMQ Demo", description = "Triggers for the four RabbitMQ messaging patterns")
public class DemoController {

	private final SimpleProducer simpleProducer;
	private final WorkQueueProducer workQueueProducer;
	private final PubSubProducer pubSubProducer;
	private final RoutingProducer routingProducer;

	@PostMapping("/simple")
	@Operation(summary = "Send to simple queue")
	public ResponseEntity<String> simple(
			@Parameter(description = "Text to send to the simple queue") @RequestParam String message) {
		simpleProducer.send(message);
		return ResponseEntity.ok("Sent to simple.queue: " + message);
	}

	@PostMapping("/work")
	@Operation(summary = "Send to work queue")
	public ResponseEntity<String> work(
			@Parameter(description = "Text to send; add dots to simulate work duration (e.g. task..)") @RequestParam String message,
			@Parameter(description = "Number of messages to dispatch (default 5)") @RequestParam(defaultValue = "5") int count) {
		workQueueProducer.send(message, count);
		return ResponseEntity.ok("Sent " + count + " messages to work.queue");
	}

	@PostMapping("/pubsub")
	@Operation(summary = "Broadcast via fanout exchange")
	public ResponseEntity<String> pubsub(
			@Parameter(description = "Text to broadcast to all fanout subscribers") @RequestParam String message) {
		pubSubProducer.send(message);
		return ResponseEntity.ok("Broadcast to pubsub.fanout: " + message);
	}

	@PostMapping("/routing")
	@Operation(summary = "Route via direct exchange")
	public ResponseEntity<String> routing(
			@Parameter(description = "Routing key — one of: info, warning, error", schema = @Schema(allowableValues = {
					"info", "warning", "error"})) @RequestParam String key,
			@Parameter(description = "Text to route") @RequestParam String message) {
		routingProducer.send(key, message);
		return ResponseEntity.ok("Routed to routing.direct with key=" + key + ": " + message);
	}
}
