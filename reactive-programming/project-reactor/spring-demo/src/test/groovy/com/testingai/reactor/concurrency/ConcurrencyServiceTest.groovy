package com.testingai.reactor.concurrency

import com.testingai.reactor.domain.SampleDataService
import reactor.test.StepVerifier
import spock.lang.Specification

class ConcurrencyServiceTest extends Specification {

    def sampleDataService = new SampleDataService()
    def concurrencyService = new ConcurrencyService(sampleDataService)

    def "subscribeOnVsPublishOn records one trace per stage, both off the calling thread"() {
        given:
        def callingThreadName = Thread.currentThread().getName()

        expect:
        StepVerifier.create(concurrencyService.subscribeOnVsPublishOn())
                .assertNext({ traces ->
                    assert traces.size() == 2
                    assert traces*.stage().toSet() == ["subscribeOn", "publishOn"].toSet()
                    assert traces*.threadName().every { it != callingThreadName }
                })
                .verifyComplete()
    }

    def "parallelDemo records one thread trace per catalog product"() {
        expect:
        StepVerifier.create(concurrencyService.parallelDemo())
                .assertNext({ traces -> assert traces.size() == sampleDataService.catalog().size() })
                .verifyComplete()
    }

    def "blockingOffload runs the blocking call on a boundedElastic thread"() {
        expect:
        StepVerifier.create(concurrencyService.blockingOffload())
                .assertNext({ threadName -> assert threadName.contains("boundedElastic") })
                .verifyComplete()
    }
}
