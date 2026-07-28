package com.testingai.reactor.streaming

import com.testingai.reactor.domain.SampleDataService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import spock.lang.Specification

class StreamingServiceTest extends Specification {

    MockWebServer mockWebServer = new MockWebServer()
    StreamingService streamingService

    def setup() {
        mockWebServer.start()
        def webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        streamingService = new StreamingService(new SampleDataService(), webClient)
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    def "fetchUpstreamProducts deserializes the upstream NDJSON product stream"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody('{"id":"P-100","name":"Wireless Mouse","priceCents":2499}\n' +
                        '{"id":"P-101","name":"Mechanical Keyboard","priceCents":8999}\n')
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))

        expect:
        StepVerifier.create(streamingService.fetchUpstreamProducts())
                .expectNextMatches({ it.id() == "P-100" })
                .expectNextMatches({ it.id() == "P-101" })
                .verifyComplete()
    }

    def "relayUpstreamTicks unwraps the upstream SSE payload into a PriceTick"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody('data:{"productId":"P-100","priceCents":2510,"timestamp":"2026-07-28T10:00:00Z"}\n\n')
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))

        expect:
        StepVerifier.create(streamingService.relayUpstreamTicks())
                .expectNextMatches({ it.productId() == "P-100" && it.priceCents() == 2510L })
                .verifyComplete()
    }
}
