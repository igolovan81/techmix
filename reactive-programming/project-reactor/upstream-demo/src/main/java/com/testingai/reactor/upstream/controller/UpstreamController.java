package com.testingai.reactor.upstream.controller;

import com.testingai.reactor.upstream.domain.PriceTick;
import com.testingai.reactor.upstream.domain.Product;
import com.testingai.reactor.upstream.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/upstream")
@RequiredArgsConstructor
public class UpstreamController {

	private static final Duration TICK_INTERVAL = Duration.ofMillis(500);
	private static final long MAX_PRICE_STEP_CENTS = 50;

	private final SampleDataService sampleDataService;

	@GetMapping(value = "/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<Product> products() {
		return Flux.fromIterable(sampleDataService.catalog());
	}

	@GetMapping(value = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<PriceTick>> ticks() {
		List<Product> catalog = sampleDataService.catalog();
		return Flux.interval(TICK_INTERVAL).map(tick -> catalog.get((int) (tick % catalog.size())))
				.map(product -> ServerSentEvent.builder(randomWalk(product)).build());
	}

	private PriceTick randomWalk(Product product) {
		long deltaCents = ThreadLocalRandom.current().nextLong(-MAX_PRICE_STEP_CENTS, MAX_PRICE_STEP_CENTS + 1);
		long walkedPriceCents = Math.max(1, product.priceCents() + deltaCents);
		return new PriceTick(product.id(), walkedPriceCents, Instant.now());
	}
}
