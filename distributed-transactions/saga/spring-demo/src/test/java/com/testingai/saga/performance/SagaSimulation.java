package com.testingai.saga.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class SagaSimulation extends Simulation {

	private static final String HAPPY_PATH_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}""";

	private static final String PAYMENT_FAILURE_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"PROCESS_PAYMENT"}""";

	private static final String SHIPPING_FAILURE_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"ARRANGE_SHIPPING"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8089")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder choreographyScenario = scenario("Choreography Checkout")
			.exec(http("Happy Path Checkout").post("/demo/saga/choreography/checkout").body(StringBody(HAPPY_PATH_BODY))
					.check(status().is(202)).check(jsonPath("$.orderId").saveAs("orderId")))
			.exec(http("Order Timeline").get("/demo/saga/choreography/orders/#{orderId}").check(status().is(200)))
			.exec(http("Forced Payment Failure").post("/demo/saga/choreography/checkout")
					.body(StringBody(PAYMENT_FAILURE_BODY)).check(status().is(202)));

	private final ScenarioBuilder orchestrationScenario = scenario("Orchestration Checkout")
			.exec(http("Happy Path Checkout").post("/demo/saga/orchestration/checkout").body(StringBody(HAPPY_PATH_BODY))
					.check(status().is(200)))
			.exec(http("Forced Shipping Failure").post("/demo/saga/orchestration/checkout")
					.body(StringBody(SHIPPING_FAILURE_BODY)).check(status().is(200)));

	{
		setUp(choreographyScenario.injectOpen(atOnceUsers(10)), orchestrationScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
