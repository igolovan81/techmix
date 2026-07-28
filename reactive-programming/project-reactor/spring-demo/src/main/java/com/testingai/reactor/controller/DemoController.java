package com.testingai.reactor.controller;

import com.testingai.reactor.basics.BasicsService;
import com.testingai.reactor.concurrency.ConcurrencyService;
import com.testingai.reactor.concurrency.ThreadTraceDto;
import com.testingai.reactor.domain.PriceTick;
import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.ProductWithDiscount;
import com.testingai.reactor.resilience.BackpressureResultDto;
import com.testingai.reactor.resilience.ResilienceService;
import com.testingai.reactor.streaming.StreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final BasicsService basicsService;
	private final ResilienceService resilienceService;
	private final ConcurrencyService concurrencyService;
	private final StreamingService streamingService;

	@GetMapping(value = "/basics/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<Product> basicsProducts() {
		return basicsService.allProducts();
	}

	@GetMapping("/basics/products/{id}")
	public Mono<ResponseEntity<Product>> basicsProductById(@PathVariable String id) {
		return basicsService.productById(id).map(ResponseEntity::ok).defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@GetMapping(value = "/basics/generated", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<Product> basicsGenerated(@RequestParam int count) {
		return basicsService.generatedProducts(count);
	}

	@GetMapping(value = "/basics/discounted", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<ProductWithDiscount> basicsDiscounted() {
		return basicsService.discountedCatalog();
	}

	@GetMapping("/resilience/backpressure")
	public Mono<BackpressureResultDto> resilienceBackpressure(@RequestParam String strategy) {
		return resilienceService.demonstrateBackpressure(strategy);
	}

	@GetMapping(value = "/resilience/retry", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<String> resilienceRetry() {
		return resilienceService.retryDemo();
	}

	@GetMapping("/resilience/timeout")
	public Mono<String> resilienceTimeout() {
		return resilienceService.timeoutDemo();
	}

	@GetMapping("/concurrency/subscribe-vs-publish-on")
	public Mono<List<ThreadTraceDto>> concurrencySubscribeVsPublishOn() {
		return concurrencyService.subscribeOnVsPublishOn();
	}

	@GetMapping("/concurrency/parallel")
	public Mono<List<ThreadTraceDto>> concurrencyParallel() {
		return concurrencyService.parallelDemo();
	}

	@GetMapping("/concurrency/blocking-offload")
	public Mono<String> concurrencyBlockingOffload() {
		return concurrencyService.blockingOffload();
	}

	@GetMapping(value = "/streaming/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<PriceTick>> streamingTicks() {
		return streamingService.localTicks();
	}

	@GetMapping(value = "/streaming/upstream/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<Product> streamingUpstreamProducts() {
		return streamingService.fetchUpstreamProducts();
	}

	@GetMapping(value = "/streaming/upstream/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<PriceTick> streamingUpstreamTicks() {
		return streamingService.relayUpstreamTicks();
	}
}
