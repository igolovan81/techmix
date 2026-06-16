package com.testingai.kafka.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load simulation for the Kafka spring-demo.
 *
 * <p>
 * Exercises four messaging patterns in parallel:
 * <ul>
 * <li>Simple Topic — single producer/consumer</li>
 * <li>Work Queue — competing consumers across two workers</li>
 * <li>PubSub — broadcast to multiple consumer groups</li>
 * <li>Partitioning — key-based routing across partitions</li>
 * </ul>
 *
 * <p>
 * Prerequisites: Kafka cluster running ({@code docker/docker-compose.yml}) and the spring-demo app running on
 * {@code localhost:8080}. Run with: {@code mvn gatling:test}
 */
public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080");

	private final ScenarioBuilder simpleScenario = scenario("Simple Topic").exec(
			http("POST /demo/simple").post("/demo/simple").formParam("message", "perf-test").check(status().is(200)));

	private final ScenarioBuilder workScenario = scenario("Work Queue").exec(http("POST /demo/work (3 msgs)")
			.post("/demo/work").formParam("message", "task..").formParam("count", "3").check(status().is(200)));

	private final ScenarioBuilder pubsubScenario = scenario("PubSub").exec(http("POST /demo/pubsub")
			.post("/demo/pubsub").formParam("message", "perf-broadcast").check(status().is(200)));

	private final ScenarioBuilder partitionScenario = scenario("Partitioning")
			.exec(http("POST /demo/partition").post("/demo/partition").formParam("key", "info")
					.formParam("message", "perf-route").check(status().is(200)));

	{
		setUp(simpleScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
				constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				workScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				pubsubScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))),
				partitionScenario.injectOpen(rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
						constantUsersPerSec(10).during(Duration.ofSeconds(30))))
				.protocols(httpProtocol).assertions(global().responseTime().percentile(95).lt(500),
						global().failedRequests().percent().lt(1.0));
	}
}
