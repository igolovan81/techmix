package com.testingai.pulsar.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load simulation for the Apache Pulsar spring-demo.
 *
 * <p>
 * Exercises five messaging patterns in parallel:
 * <ul>
 * <li>Simple Topic — Exclusive subscription, single producer/consumer</li>
 * <li>Work Queue — Shared subscription, competing consumers A and B</li>
 * <li>PubSub — two independent Exclusive subscriptions (broadcast)</li>
 * <li>Routing — Key_Shared subscription, key-sticky consumer assignment</li>
 * <li>Transaction — atomic batch publish</li>
 * </ul>
 *
 * <p>
 * Prerequisites: Pulsar standalone running ({@code docker/docker-compose.yml}) and the spring-demo app running on
 * {@code localhost:8083}. Run with: {@code mvn gatling:test}
 */
public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8083");

	private final ScenarioBuilder simpleScenario = scenario("Simple Topic").exec(
			http("POST /demo/simple").post("/demo/simple").formParam("message", "perf-test").check(status().is(200)));

	private final ScenarioBuilder workScenario = scenario("Work Queue").exec(http("POST /demo/work (3 msgs)")
			.post("/demo/work").formParam("message", "task..").formParam("count", "3").check(status().is(200)));

	private final ScenarioBuilder pubsubScenario = scenario("PubSub").exec(http("POST /demo/pubsub")
			.post("/demo/pubsub").formParam("message", "perf-broadcast").check(status().is(200)));

	private final ScenarioBuilder routingScenario = scenario("Routing (Key_Shared)").exec(http("POST /demo/routing")
			.post("/demo/routing").formParam("key", "info").formParam("message", "perf-route").check(status().is(200)));

	private final ScenarioBuilder transactionScenario = scenario("Transaction").exec(http("POST /demo/transaction")
			.post("/demo/transaction").formParam("message", "perf-tx").formParam("count", "3").check(status().is(200)));

	{
		setUp(simpleScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
				constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				workScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				pubsubScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				routingScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				transactionScenario.injectOpen(rampUsersPerSec(1).to(5).during(Duration.ofSeconds(30)),
						constantUsersPerSec(5).during(Duration.ofSeconds(30))))
				.protocols(httpProtocol).assertions(global().responseTime().percentile(95).lt(500),
						global().failedRequests().percent().lt(1.0));
	}
}
