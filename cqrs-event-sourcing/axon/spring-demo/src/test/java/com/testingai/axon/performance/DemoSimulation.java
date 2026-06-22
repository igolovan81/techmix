package com.testingai.axon.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8086")
			.acceptHeader("application/json").contentTypeHeader("application/json");

	private final ScenarioBuilder orderLifecycleScenario = scenario("Order Lifecycle")
			.exec(exec(http("Create Order").post("/demo/orders").body(StringBody("{\"customerId\":\"load-test\"}"))
					.check(status().is(200))))
			.exec(exec(http("Get All Orders").get("/demo/orders").check(status().is(200))));

	{
		setUp(orderLifecycleScenario.injectOpen(atOnceUsers(10))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(30));
	}
}
