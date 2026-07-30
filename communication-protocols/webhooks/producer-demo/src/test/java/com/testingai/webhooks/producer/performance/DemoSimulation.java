package com.testingai.webhooks.producer.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8096")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("Webhooks Producer Demo")
			.exec(http("Trigger order.created").post("/orders/order-1/events/created").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.paid").post("/orders/order-1/events/paid").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.shipped").post("/orders/order-1/events/shipped").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.cancelled").post("/orders/order-2/events/cancelled").check(status().is(202)));

	{
		// 2 users, ramped a few seconds apart, matching the gRPC/GraphQL demos' pacing style. Run with a healthy
		// consumer-demo and at least one subscription registered so the console logs show real dispatch/success
		// activity, not just 202s with empty delivery-id lists.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
