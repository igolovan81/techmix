package com.testingai.reactor.controller

import com.testingai.reactor.basics.BasicsService
import com.testingai.reactor.concurrency.ConcurrencyService
import com.testingai.reactor.domain.Product
import com.testingai.reactor.resilience.BackpressureResultDto
import com.testingai.reactor.resilience.ResilienceService
import com.testingai.reactor.streaming.StreamingService
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DemoControllerTest extends Specification {

    def basicsService = Mock(BasicsService)
    def resilienceService = Mock(ResilienceService)
    def concurrencyService = Mock(ConcurrencyService)
    def streamingService = Mock(StreamingService)

    def webTestClient = WebTestClient.bindToController(
            new DemoController(basicsService, resilienceService, concurrencyService, streamingService))
            .build()

    def "GET /demo/basics/products/{id} returns 200 with the product when found"() {
        given:
        basicsService.productById("P-100") >> Mono.just(new Product("P-100", "Wireless Mouse", 2499))

        expect:
        webTestClient.get().uri("/demo/basics/products/P-100")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product)
                .isEqualTo(new Product("P-100", "Wireless Mouse", 2499))
    }

    def "GET /demo/basics/products/{id} returns 404 when the product is unknown"() {
        given:
        basicsService.productById("missing") >> Mono.empty()

        expect:
        webTestClient.get().uri("/demo/basics/products/missing")
                .exchange()
                .expectStatus().isNotFound()
    }

    def "GET /demo/resilience/backpressure returns the backpressure result"() {
        given:
        resilienceService.demonstrateBackpressure("drop") >> Mono.just(new BackpressureResultDto("drop", 200, 5, 195))

        expect:
        webTestClient.get().uri("/demo/resilience/backpressure?strategy=drop")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BackpressureResultDto)
                .isEqualTo(new BackpressureResultDto("drop", 200, 5, 195))
    }

    def "GET /demo/concurrency/blocking-offload returns the offloaded thread name"() {
        given:
        concurrencyService.blockingOffload() >> Mono.just("boundedElastic-1")

        expect:
        webTestClient.get().uri("/demo/concurrency/blocking-offload")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String)
                .isEqualTo("boundedElastic-1")
    }

    def "GET /demo/streaming/upstream/products streams products from the streaming service"() {
        given:
        streamingService.fetchUpstreamProducts() >> Flux.just(new Product("P-100", "Wireless Mouse", 2499))

        expect:
        webTestClient.get().uri("/demo/streaming/upstream/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product)
                .isEqualTo([new Product("P-100", "Wireless Mouse", 2499)])
    }
}
