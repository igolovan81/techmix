package com.testingai.camunda.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private static final String LOW_VALUE_ORDER_BODY = """
			{"customerId":"load-test","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":null}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8093")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("Camunda Demo")
			.exec(http("Start Order - Low Value (happy path)").post("/demo/camunda/orders")
					.body(StringBody(LOW_VALUE_ORDER_BODY)).check(status().is(200)));

	{
		// 2 users, ramped a few seconds apart, matching every other DemoSimulation's pacing in this repo. High-value
		// (approval-required) and failAt flows aren't load-tested — they need a human-shaped follow-up call mid-flight,
		// not a fire-and-forget request, so this simulation only covers the straight-line happy path.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
