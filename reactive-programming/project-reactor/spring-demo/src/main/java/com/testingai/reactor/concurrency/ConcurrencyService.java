package com.testingai.reactor.concurrency;

import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class ConcurrencyService {

	private static final Duration BLOCKING_CALL_DURATION = Duration.ofMillis(50);

	private final SampleDataService sampleDataService;

	public Mono<List<ThreadTraceDto>> subscribeOnVsPublishOn() {
		Mono<ThreadTraceDto> subscribeOnTrace = Mono
				.fromSupplier(() -> new ThreadTraceDto("subscribeOn", Thread.currentThread().getName()))
				.subscribeOn(Schedulers.boundedElastic());

		Mono<ThreadTraceDto> publishOnTrace = Mono.just("assembly").publishOn(Schedulers.parallel())
				.map(ignored -> new ThreadTraceDto("publishOn", Thread.currentThread().getName()));

		return Flux.merge(subscribeOnTrace, publishOnTrace).collectList();
	}

	public Mono<List<ThreadTraceDto>> parallelDemo() {
		List<ThreadTraceDto> traces = new CopyOnWriteArrayList<>();
		return Flux.fromIterable(sampleDataService.catalog()).parallel(4).runOn(Schedulers.parallel())
				.doOnNext(product -> traces.add(new ThreadTraceDto(product.id(), Thread.currentThread().getName())))
				.sequential().then(Mono.fromSupplier(() -> List.copyOf(traces)));
	}

	public Mono<String> blockingOffload() {
		return Mono.fromCallable(this::simulateBlockingCall).subscribeOn(Schedulers.boundedElastic());
	}

	private String simulateBlockingCall() {
		try {
			Thread.sleep(BLOCKING_CALL_DURATION.toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return Thread.currentThread().getName();
	}
}
