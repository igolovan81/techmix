package com.testingai.reactor.resilience

import reactor.test.StepVerifier
import spock.lang.Specification
import spock.lang.Unroll

class ResilienceServiceTest extends Specification {

    def resilienceService = new ResilienceService()

    @Unroll
    def "demonstrateBackpressure(#strategy) accounts for every emitted item and overflows the slow consumer"() {
        expect:
        StepVerifier.create(resilienceService.demonstrateBackpressure(strategy))
                .assertNext({ result ->
                    assert result.strategy() == strategy
                    assert result.emitted() == 200L
                    assert result.processed() + result.droppedOrBuffered() == 200L
                    assert result.droppedOrBuffered() > 0L
                })
                .verifyComplete()

        where:
        strategy << ["buffer", "drop"]
    }

    def "retryDemo resolves to success or the exhausted-retries fallback"() {
        expect:
        StepVerifier.create(resilienceService.retryDemo())
                .expectNextMatches({ it == "success" || it == "fallback-after-retries-exhausted" })
                .verifyComplete()
    }

    def "retryDemo resolves to success in the overwhelming majority of runs"() {
        given:
        int successes = 0

        when:
        50.times {
            if (resilienceService.retryDemo().blockFirst() == "success") {
                successes++
            }
        }

        then:
        successes >= 45
    }

    def "timeoutDemo returns the fallback once the simulated call exceeds the timeout"() {
        expect:
        StepVerifier.create(resilienceService.timeoutDemo())
                .expectNext("fallback-after-timeout")
                .verifyComplete()
    }
}
