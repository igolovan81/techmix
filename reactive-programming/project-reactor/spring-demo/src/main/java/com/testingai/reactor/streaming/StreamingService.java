package com.testingai.reactor.streaming;

import com.testingai.reactor.domain.PriceTick;
import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class StreamingService {

	private static final Duration TICK_INTERVAL = Duration.ofMillis(500);
	private static final long MAX_PRICE_STEP_CENTS = 50;

	private final SampleDataService sampleDataService;
	private final WebClient upstreamWebClient;

	public Flux<ServerSentEvent<PriceTick>> localTicks() {
		List<Product> catalog = sampleDataService.catalog();
		return Flux.interval(TICK_INTERVAL).map(tick -> catalog.get((int) (tick % catalog.size())))
				.map(product -> ServerSentEvent.builder(randomWalk(product)).build());
	}

	public Flux<Product> fetchUpstreamProducts() {
		return upstreamWebClient.get().uri("/upstream/products").retrieve().bodyToFlux(Product.class);
	}

	public Flux<PriceTick> relayUpstreamTicks() {
		return upstreamWebClient.get().uri("/upstream/ticks").accept(MediaType.TEXT_EVENT_STREAM).retrieve()
				.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<PriceTick>>() {
				}).map(ServerSentEvent::data);
	}

	private PriceTick randomWalk(Product product) {
		long deltaCents = ThreadLocalRandom.current().nextLong(-MAX_PRICE_STEP_CENTS, MAX_PRICE_STEP_CENTS + 1);
		long walkedPriceCents = Math.max(1, product.priceCents() + deltaCents);
		return new PriceTick(product.id(), walkedPriceCents, Instant.now());
	}
}
