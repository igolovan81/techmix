package com.testingai.disruptor.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8100")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder disruptorScenario = scenario("LMAX Disruptor Patterns")
			.exec(http("Single Handler").post("/demo/disruptor/single?eventCount=1000").check(status().is(200)))
			.exec(http("Parallel Handlers").post("/demo/disruptor/parallel?eventCount=1000").check(status().is(200)))
			.exec(http("Diamond Dependency Graph").post("/demo/disruptor/diamond?eventCount=1000")
					.check(status().is(200)))
			.exec(http("Producer Comparison").post("/demo/disruptor/producer?eventCount=1000&threads=4")
					.check(status().is(200)))
			.exec(http("Wait Strategy Comparison").post("/demo/disruptor/waitstrategy?eventCount=5000")
					.check(status().is(200)))
			.exec(http("Simulated Handler Errors").post("/demo/disruptor/errors?eventCount=1000")
					.check(status().is(200)));

	{
		setUp(disruptorScenario.injectOpen(atOnceUsers(5))).protocols(httpProtocol).maxDuration(Duration.ofSeconds(60));
	}
}
