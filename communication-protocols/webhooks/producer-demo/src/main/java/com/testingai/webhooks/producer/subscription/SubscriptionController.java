package com.testingai.webhooks.producer.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

	public record SubscriptionRequest(String callbackUrl, String secret, Set<String> eventTypes) {
	}

	public record SubscriptionResponse(String id, String callbackUrl, Set<String> eventTypes) {
	}

	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@PostMapping
	public ResponseEntity<SubscriptionResponse> register(@RequestBody SubscriptionRequest request) {
		Subscription subscription = subscriptionService.register(request.callbackUrl(), request.secret(),
				request.eventTypes());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(subscription));
	}

	@GetMapping
	public List<SubscriptionResponse> list() {
		return subscriptionService.findAll().stream().map(this::toResponse).toList();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		return subscriptionService.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private SubscriptionResponse toResponse(Subscription subscription) {
		return new SubscriptionResponse(subscription.id(), subscription.callbackUrl(), subscription.eventTypes());
	}
}
