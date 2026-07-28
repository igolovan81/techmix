package com.testingai.reactor.upstream.controller

import com.testingai.reactor.upstream.domain.PriceTick
import com.testingai.reactor.upstream.domain.Product
import com.testingai.reactor.upstream.domain.SampleDataService
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier
import spock.lang.Specification

class UpstreamControllerTest extends Specification {

    def sampleDataService = new SampleDataService()
    def webTestClient = WebTestClient.bindToController(new UpstreamController(sampleDataService)).build()

    def "GET /upstream/products streams the full catalog"() {
        expect:
        webTestClient.get().uri("/upstream/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product)
                .hasSize(sampleDataService.catalog().size())
    }

    def "GET /upstream/ticks streams price ticks over time"() {
        given:
        def result = webTestClient.get().uri("/upstream/ticks")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(PriceTick)

        expect:
        StepVerifier.create(result.getResponseBody().take(2))
                .expectNextCount(2)
                .verifyComplete()
    }
}
