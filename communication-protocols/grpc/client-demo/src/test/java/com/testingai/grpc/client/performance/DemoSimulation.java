package com.testingai.grpc.client.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private static final String CLIENT_STREAMING_BODY = """
			[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}]""";

	private static final String BIDI_STREAMING_BODY = """
			[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"SHIPPED"}]""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8091")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("gRPC Demo")
			.exec(http("Unary - Get Product").get("/demo/grpc/unary/products/p1").check(status().is(200)))
			.exec(http("Server Streaming - List Products").get("/demo/grpc/server-streaming/products")
					.check(status().is(200)))
			.exec(http("Client Streaming - Upload Orders").post("/demo/grpc/client-streaming/orders")
					.body(StringBody(CLIENT_STREAMING_BODY)).check(status().is(200)))
			.exec(http("Bidi Streaming - Order Status").post("/demo/grpc/bidi-streaming/order-status")
					.body(StringBody(BIDI_STREAMING_BODY)).check(status().is(200)));

	{
		setUp(demoScenario.injectOpen(atOnceUsers(10))).protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
