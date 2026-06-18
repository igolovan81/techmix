package com.testingai.mongodb.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8084")
			.acceptHeader("application/json").contentTypeHeader("application/json");

	private final ScenarioBuilder createProductScenario = scenario("Create Product")
			.exec(exec(http("Create Product").post("/demo/products")
					.body(io.gatling.javaapi.core.CoreDsl
							.StringBody("{\"name\":\"Load Test Widget\",\"price\":9.99,\"stock\":1000}"))
					.check(status().is(200))));

	private final ScenarioBuilder aggregationScenario = scenario("Aggregation")
			.exec(exec(http("Aggregation").get("/demo/aggregation").check(status().is(200))));

	{
		setUp(createProductScenario.injectOpen(atOnceUsers(10)), aggregationScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
