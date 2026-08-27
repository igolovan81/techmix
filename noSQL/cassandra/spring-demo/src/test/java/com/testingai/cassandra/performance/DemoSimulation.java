package com.testingai.cassandra.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8085")
			.acceptHeader("application/json").contentTypeHeader("application/json");

	private final Iterator<Map<String, Object>> productFeeder = Stream
			.generate(() -> Map.<String, Object>of("name", "Widget-" + UUID.randomUUID(), "price", 9.99, "stock", 100))
			.iterator();

	private final ScenarioBuilder demoScenario = scenario("Cassandra Demo").feed(productFeeder)
			.exec(http("Create Product").post("/demo/products")
					.body(io.gatling.javaapi.core.CoreDsl
							.StringBody("{\"name\":\"#{name}\",\"price\":#{price},\"stock\":#{stock}}"))
					.check(status().is(200)).check(jsonPath("$.id").saveAs("productId")))
			.exec(http("Get Product (records TTL view)").get("/demo/products/#{productId}").check(status().is(200)))
			.exec(http("Consistency Read - ONE").get("/demo/products/#{productId}/consistency?level=ONE")
					.check(status().is(200)))
			.exec(http("Consistency Read - QUORUM").get("/demo/products/#{productId}/consistency?level=QUORUM")
					.check(status().is(200)))
			.exec(http("Place Order").post("/demo/orders")
					.body(io.gatling.javaapi.core.CoreDsl.StringBody(
							"{\"customerId\":\"cust-#{productId}\",\"productId\":\"#{productId}\",\"quantity\":2}"))
					.check(status().is(200)))
			.exec(http("Order Count").get("/demo/products/#{productId}/order-count").check(status().is(200)))
			.exec(http("Recently Viewed").get("/demo/products/#{productId}/recently-viewed").check(status().is(200)));

	{
		setUp(demoScenario.injectOpen(atOnceUsers(10))).protocols(httpProtocol);
	}
}
