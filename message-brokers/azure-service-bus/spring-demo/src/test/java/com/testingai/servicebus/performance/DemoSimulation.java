package com.testingai.servicebus.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load simulation for the Azure Service Bus spring-demo.
 *
 * <p>
 * Exercises seven messaging patterns in parallel:
 * <ul>
 * <li>Simple Queue — basic send/receive</li>
 * <li>Work Queue — competing consumers</li>
 * <li>Pub/Sub — topic with multiple subscriptions</li>
 * <li>Routing — topic filtered by severity level (info / error)</li>
 * <li>DLQ — dead-letter queue via simulated failures</li>
 * <li>Sessions — ordered delivery per session ID</li>
 * <li>Transactions — atomic send/complete</li>
 * </ul>
 *
 * <p>
 * Prerequisites: Service Bus emulator running ({@code docker/docker-compose.yml}) and the spring-demo app running on
 * {@code localhost:8082}. Run with: {@code mvn gatling:test}
 */
public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8082")
			.acceptHeader("application/json");

	private final ScenarioBuilder simple = scenario("Simple Queue")
			.exec(http("simple").post("/api/simple/send").queryParam("message", "perf-message"));

	private final ScenarioBuilder work = scenario("Work Queue")
			.exec(http("work").post("/api/work/send").queryParam("message", "perf-task"));

	private final ScenarioBuilder pubsub = scenario("Pub/Sub")
			.exec(http("pubsub").post("/api/pubsub/publish").queryParam("message", "perf-broadcast"));

	private final ScenarioBuilder routing = scenario("Routing")
			.exec(http("routing-info").post("/api/routing/publish").queryParam("level", "info").queryParam("message",
					"perf-info"))
			.exec(http("routing-error").post("/api/routing/publish").queryParam("level", "error").queryParam("message",
					"perf-error"));

	private final ScenarioBuilder dlq = scenario("DLQ")
			.exec(http("dlq").post("/api/dlq/send").queryParam("message", "perf-risky"));

	private final ScenarioBuilder session = scenario("Sessions").exec(http("session").post("/api/session/send")
			.queryParam("message", "perf-session").queryParam("sessionId", "perf-session-1"));

	private final ScenarioBuilder tx = scenario("Transactions")
			.exec(http("tx").post("/api/tx/send").queryParam("message", "perf-tx").queryParam("count", "3"));

	{
		setUp(simple.injectOpen(atOnceUsers(10)), work.injectOpen(atOnceUsers(10)), pubsub.injectOpen(atOnceUsers(10)),
				routing.injectOpen(atOnceUsers(10)), dlq.injectOpen(atOnceUsers(10)),
				session.injectOpen(atOnceUsers(10)), tx.injectOpen(atOnceUsers(10))).protocols(httpProtocol)
				.maxDuration(Duration.ofMinutes(2));
	}
}
