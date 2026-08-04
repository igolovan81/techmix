package com.testingai.websockets.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.regex;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.gatling.javaapi.http.HttpDsl.ws;

public class DemoSimulation extends Simulation {

	private static final String STOMP_CONNECT_FRAME = "CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000";
	private static final String STOMP_SUBSCRIBE_FRAME = "SUBSCRIBE\nid:sub-0\ndestination:/topic/orders\n\n\u0000";
	private static final String STOMP_HEARTBEAT_FRAME = "\n";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8098");

	private final ScenarioBuilder demoScenario = scenario("WebSocket Demo")
			.exec(http("Create Order").post("/api/orders").check(status().is(200))
					.check(jsonPath("$.id").saveAs("orderId")))
			.exec(ws("Connect Raw").wsName("raw").connect("/ws/raw/orders"))
			.exec(ws("Connect STOMP").wsName("stomp").connect("/ws-stomp-native"))
			.exec(ws("STOMP Connect Frame").wsName("stomp").sendText(STOMP_CONNECT_FRAME).await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("stomp-connected-check").check(regex("CONNECTED"))))
			.exec(ws("STOMP Subscribe").wsName("stomp").sendText(STOMP_SUBSCRIBE_FRAME)).pause(Duration.ofMillis(200))
			.exec(http("Advance Order").post("/api/orders/#{orderId}/advance").check(status().is(200)))
			.exec(ws("Await Raw Broadcast").wsName("raw").sendText("ping").await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("raw-broadcast-check").check(regex("#{orderId}"))))
			.exec(ws("Await STOMP Message").wsName("stomp").sendText(STOMP_HEARTBEAT_FRAME).await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("stomp-message-check").check(regex("MESSAGE"))))
			.exec(ws("Close Raw").wsName("raw").close()).exec(ws("Close STOMP").wsName("stomp").close());

	{
		// 2 users, ramped a few seconds apart, matching the pacing convention from graphql/spring-demo's
		// DemoSimulation.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
