package com.testingai.freemarker.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8088");

	private final ScenarioBuilder pagesScenario = scenario("Pages")
			.exec(http("Products Page").get("/pages/products").check(status().is(200)))
			.exec(http("Order Detail Page").get("/pages/orders/o1").check(status().is(200)));

	private final ScenarioBuilder capabilitiesScenario = scenario("Capabilities")
			.exec(http("Data Model").get("/demo/data-model").check(status().is(200)))
			.exec(http("If List").get("/demo/directives/if-list").check(status().is(200)))
			.exec(http("Switch").get("/demo/directives/switch").check(status().is(200)))
			.exec(http("Macros").get("/demo/macros").check(status().is(200)))
			.exec(http("Functions").get("/demo/functions").check(status().is(200)))
			.exec(http("Builtins").get("/demo/builtins").check(status().is(200)))
			.exec(http("Composition").get("/demo/composition").check(status().is(200)))
			.exec(http("Null Safety").get("/demo/null-safety").check(status().is(200)));

	{
		setUp(pagesScenario.injectOpen(atOnceUsers(10)), capabilitiesScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
