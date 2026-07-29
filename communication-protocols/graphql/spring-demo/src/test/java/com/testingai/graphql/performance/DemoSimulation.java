package com.testingai.graphql.performance;

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

	private static final String PRODUCTS_QUERY_BODY = """
			{"query":"{ products { id name priceCents reviews { id author rating } } }"}""";

	private static final String PRODUCT_QUERY_BODY = """
			{"query":"{ product(id: \\"p1\\") { id name priceCents } }"}""";

	private static final String ADD_REVIEW_MUTATION_BODY = """
			{"query":"mutation { addReview(input: { productId: \\"p1\\", author: \\"LoadTest\\", rating: 5, comment: \\"Load test review\\" }) { id } }"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8092")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("GraphQL Demo")
			.exec(http("Query - Products with Reviews")
					.post("/graphql").body(StringBody(PRODUCTS_QUERY_BODY)).check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Query - Product by Id").post("/graphql").body(StringBody(PRODUCT_QUERY_BODY))
					.check(status().is(200)))
			.pause(Duration.ofMillis(500)).exec(http("Mutation - Add Review").post("/graphql")
					.body(StringBody(ADD_REVIEW_MUTATION_BODY)).check(status().is(200)));

	{
		// 2 users, ramped a few seconds apart, so each user's calls stay visually distinct in the logs instead of
		// interleaving with a burst of traffic — matches grpc/client-demo's DemoSimulation pacing.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
