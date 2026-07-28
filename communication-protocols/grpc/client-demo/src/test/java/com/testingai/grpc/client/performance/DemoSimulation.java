package com.testingai.grpc.client.performance;

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

	private static final String CLIENT_STREAMING_BODY = """
			[{"productId":"p1","quantity":2},{"productId":"p5","quantity":1},{"productId":"p9","quantity":3},
			{"productId":"p12","quantity":1},{"productId":"p16","quantity":2},{"productId":"p20","quantity":1},
			{"productId":"p23","quantity":4},{"productId":"p27","quantity":1},{"productId":"p31","quantity":2},
			{"productId":"p34","quantity":1},{"productId":"p38","quantity":3},{"productId":"p40","quantity":1}]""";

	private static final String BIDI_STREAMING_BODY = """
			[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"PACKED"},
			{"orderId":"o1","status":"SHIPPED"},{"orderId":"o1","status":"OUT_FOR_DELIVERY"},
			{"orderId":"o1","status":"DELIVERED"},{"orderId":"o2","status":"PLACED"},
			{"orderId":"o2","status":"PACKED"},{"orderId":"o2","status":"SHIPPED"}]""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8091")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("gRPC Demo")
			.exec(http("Unary - Get Product").get("/demo/grpc/unary/products/p1").check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Server Streaming - List Products").get("/demo/grpc/server-streaming/products")
					.check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Client Streaming - Upload Orders").post("/demo/grpc/client-streaming/orders")
					.body(StringBody(CLIENT_STREAMING_BODY)).check(status().is(200)))
			.pause(Duration.ofMillis(500))
			.exec(http("Bidi Streaming - Order Status").post("/demo/grpc/bidi-streaming/order-status")
					.body(StringBody(BIDI_STREAMING_BODY)).check(status().is(200)));

	{
		// 2 users, ramped a few seconds apart, so each user's four streaming calls stay
		// visually distinct in the logs instead of interleaving with a burst of traffic.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
