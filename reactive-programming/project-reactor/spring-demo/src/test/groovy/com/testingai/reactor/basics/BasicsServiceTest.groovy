package com.testingai.reactor.basics

import com.testingai.reactor.domain.Product
import com.testingai.reactor.domain.SampleDataService
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import spock.lang.Specification

class BasicsServiceTest extends Specification {

    def sampleDataService = new SampleDataService()
    def basicsService = new BasicsService(sampleDataService)

    def "allProducts emits every catalog product"() {
        expect:
        StepVerifier.create(basicsService.allProducts())
                .expectNextCount(sampleDataService.catalog().size())
                .verifyComplete()
    }

    def "productById emits the matching product"() {
        expect:
        StepVerifier.create(basicsService.productById("P-101"))
                .expectNextMatches({ it.name() == "Mechanical Keyboard" })
                .verifyComplete()
    }

    def "productById completes empty for an unknown id"() {
        expect:
        StepVerifier.create(basicsService.productById("does-not-exist"))
                .verifyComplete()
    }

    def "generatedProducts emits exactly the requested count"() {
        expect:
        StepVerifier.create(basicsService.generatedProducts(7))
                .expectNextCount(7)
                .verifyComplete()
    }

    def "discountedCatalog applies the standard 10% discount to every product"() {
        expect:
        StepVerifier.create(basicsService.discountedCatalog())
                .thenConsumeWhile({ it.discountedPriceCents() == Math.round(it.product().priceCents() * 0.9) })
                .verifyComplete()
    }

    def "combinedViaConcat plays the first flux to completion before the second"() {
        given:
        def first = Flux.just(new Product("A", "A", 100))
        def second = Flux.just(new Product("B", "B", 200))

        expect:
        StepVerifier.create(basicsService.combinedViaConcat(first, second))
                .expectNextMatches({ it.id() == "A" })
                .expectNextMatches({ it.id() == "B" })
                .verifyComplete()
    }

    def "combinedViaMerge emits both sources without dropping any items"() {
        given:
        def first = Flux.just(new Product("A", "A", 100))
        def second = Flux.just(new Product("B", "B", 200))

        expect:
        StepVerifier.create(basicsService.combinedViaMerge(first, second))
                .recordWith({ [] as Set })
                .expectNextCount(2)
                .consumeRecordedWith({ ids -> assert ids*.id().toSet() == ["A", "B"].toSet() })
                .verifyComplete()
    }
}
