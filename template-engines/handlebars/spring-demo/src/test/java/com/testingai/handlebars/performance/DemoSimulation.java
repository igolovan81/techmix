package com.testingai.handlebars.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8087");

	private final ScenarioBuilder pagesScenario = scenario("Pages")
			.exec(http("Products Page").get("/pages/products").check(status().is(200)))
			.exec(http("Order Detail Page").get("/pages/orders/o1").check(status().is(200)));

	private final ScenarioBuilder capabilitiesScenario = scenario("Capabilities")
			.exec(http("Variables").get("/demo/variables").check(status().is(200)))
			.exec(http("Builtin Helpers").get("/demo/helpers/builtin").check(status().is(200)))
			.exec(http("Custom Helper").get("/demo/helpers/custom").check(status().is(200)))
			.exec(http("Partials").get("/demo/partials").check(status().is(200)))
			.exec(http("Layout").get("/demo/layout").check(status().is(200)))
			.exec(http("Subexpressions").get("/demo/subexpressions").check(status().is(200)))
			.exec(http("Precompiled").get("/demo/precompiled").check(status().is(200)));

	{
		setUp(pagesScenario.injectOpen(atOnceUsers(10)), capabilitiesScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
