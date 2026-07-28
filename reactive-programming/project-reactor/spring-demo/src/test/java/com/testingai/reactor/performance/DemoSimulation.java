package com.testingai.reactor.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8094");

	private final ScenarioBuilder demoScenario = scenario("Project Reactor Demo")
			.exec(http("Basics - All Products").get("/demo/basics/products").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Basics - Product By Id").get("/demo/basics/products/P-100").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Basics - Generated").get("/demo/basics/generated?count=5").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Basics - Discounted").get("/demo/basics/discounted").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Resilience - Backpressure Buffer")
					.get("/demo/resilience/backpressure?strategy=buffer").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Resilience - Backpressure Drop").get("/demo/resilience/backpressure?strategy=drop")
					.check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Resilience - Retry").get("/demo/resilience/retry").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Resilience - Timeout").get("/demo/resilience/timeout").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Concurrency - Subscribe vs Publish On").get("/demo/concurrency/subscribe-vs-publish-on")
					.check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Concurrency - Parallel").get("/demo/concurrency/parallel").check(status().is(200)))
			.pause(Duration.ofMillis(200))
			.exec(http("Concurrency - Blocking Offload").get("/demo/concurrency/blocking-offload")
					.check(status().is(200)))
			.pause(Duration.ofMillis(200)).exec(http("Streaming - Upstream Products")
					.get("/demo/streaming/upstream/products").check(status().is(200)));

	{
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(60));
	}
}
