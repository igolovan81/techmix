package com.testingai.redis.controller;

import com.testingai.redis.fanout.FanoutProducer;
import com.testingai.redis.pending.PendingProducer;
import com.testingai.redis.pubsub.PubSubPublisher;
import com.testingai.redis.simple.SimpleProducer;
import com.testingai.redis.trimming.TrimmingProducer;
import com.testingai.redis.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Redis Streams Demo")
public class DemoController {

	private final SimpleProducer simpleProducer;
	private final WorkQueueProducer workQueueProducer;
	private final FanoutProducer fanoutProducer;
	private final PendingProducer pendingProducer;
	private final TrimmingProducer trimmingProducer;
	private final PubSubPublisher pubSubPublisher;

	@PostMapping("/simple")
	@Operation(summary = "Simple streaming — XADD to {streams}:simple")
	public ResponseEntity<String> simple(@RequestParam String message) {
		simpleProducer.send(message);
		return ResponseEntity.ok("sent: " + message);
	}

	@PostMapping("/work")
	@Operation(summary = "Work queue — XADD to {streams}:work (work-group)")
	public ResponseEntity<String> work(@RequestParam String message, @RequestParam(defaultValue = "1") int count) {
		for (int i = 0; i < count; i++) {
			workQueueProducer.send(message);
		}
		return ResponseEntity.ok("sent " + count + " message(s)");
	}

	@PostMapping("/fanout")
	@Operation(summary = "Fanout — XADD to {streams}:fanout (group-a and group-b)")
	public ResponseEntity<String> fanout(@RequestParam String message) {
		fanoutProducer.send(message);
		return ResponseEntity.ok("broadcast: " + message);
	}

	@PostMapping("/pending")
	@Operation(summary = "Pending & retry — XADD to {streams}:pending, 5% failure leaves entry in PEL")
	public ResponseEntity<String> pending(@RequestParam String message, @RequestParam(defaultValue = "1") int count) {
		for (int i = 0; i < count; i++) {
			pendingProducer.send(message);
		}
		return ResponseEntity.ok("sent " + count + " message(s) to pending stream");
	}

	@PostMapping("/trimming")
	@Operation(summary = "Stream trimming — XADD with MAXLEN 100")
	public ResponseEntity<String> trimming(@RequestParam String message) {
		trimmingProducer.send(message);
		return ResponseEntity.ok("sent with trim: " + message);
	}

	@PostMapping("/pubsub")
	@Operation(summary = "Native Pub/Sub — PUBLISH to demo:pubsub")
	public ResponseEntity<String> pubsub(@RequestParam String message) {
		pubSubPublisher.publish(message);
		return ResponseEntity.ok("published: " + message);
	}
}
