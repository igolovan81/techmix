package com.testingai.webhooks.consumer.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.webhooks.consumer.failure.FailureSimulationState;
import com.testingai.webhooks.consumer.security.HmacVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Slf4j
public class WebhookReceiverController {

	private final HmacVerifier hmacVerifier;
	private final FailureSimulationState failureSimulationState;
	private final ReceivedEventStore receivedEventStore;
	private final ObjectMapper objectMapper;
	private final String secret;

	public WebhookReceiverController(HmacVerifier hmacVerifier, FailureSimulationState failureSimulationState,
			ReceivedEventStore receivedEventStore, ObjectMapper objectMapper,
			@Value("${webhook.secret}") String secret) {
		this.hmacVerifier = hmacVerifier;
		this.failureSimulationState = failureSimulationState;
		this.receivedEventStore = receivedEventStore;
		this.objectMapper = objectMapper;
		this.secret = secret;
	}

	@PostMapping(value = "/webhooks/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> receive(@RequestHeader("X-Webhook-Id") String deliveryId,
			@RequestHeader("X-Webhook-Event") String eventType,
			@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
			@RequestBody String rawBody) throws IOException {
		if (failureSimulationState.consumeFailure()) {
			log.warn("delivery {} rejected by simulated failure ({} remaining)", deliveryId,
					failureSimulationState.remaining());
			return ResponseEntity.internalServerError().build();
		}
		if (!hmacVerifier.verify(secret, rawBody, signature)) {
			log.warn("delivery {} rejected: signature verification failed", deliveryId);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		IncomingOrderEvent event = objectMapper.readValue(rawBody, IncomingOrderEvent.class);
		boolean isNew = receivedEventStore.recordIfNew(deliveryId, eventType, event.orderId());
		if (isNew) {
			log.info("delivery {} received: event {} for order {}", deliveryId, eventType, event.orderId());
		} else {
			log.info("delivery {} is a duplicate of an already-recorded event, ignoring", deliveryId);
		}
		return ResponseEntity.ok().build();
	}
}
