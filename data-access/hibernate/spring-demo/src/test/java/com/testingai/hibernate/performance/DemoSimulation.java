package com.testingai.hibernate.performance;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol =
			http.baseUrl("http://localhost:8105").acceptHeader("application/json");

	private final ScenarioBuilder catalogAndFetchScenario = scenario("Catalog and association fetching")
			.exec(http("List all library items").get("/demo/library-items").check(status().is(200)))
			.exec(http("List all people").get("/demo/people").check(status().is(200)))
			.exec(http("Demonstrate N+1").get("/demo/books/n-plus-one").check(status().is(200)))
			.exec(http("Fetch with JOIN FETCH").get("/demo/books/join-fetch").check(status().is(200)))
			.exec(http("Fetch with @EntityGraph").get("/demo/books/entity-graph").check(status().is(200)))
			.exec(http("Fetch with @BatchSize").get("/demo/library-items/batch-fetch").check(status().is(200)));

	{
		setUp(catalogAndFetchScenario.injectOpen(constantUsersPerSec(10).during(Duration.ofSeconds(30))))
				.protocols(httpProtocol);
	}
}
