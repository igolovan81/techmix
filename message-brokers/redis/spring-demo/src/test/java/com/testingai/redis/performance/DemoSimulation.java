package com.testingai.redis.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load simulation for the Redis Streams spring-demo.
 *
 * <p>
 * Exercises four messaging patterns in parallel:
 * <ul>
 * <li>Simple Stream — basic produce/consume via Redis Streams</li>
 * <li>Work Queue — consumer group with competing consumers</li>
 * <li>Fanout — broadcast to multiple independent consumer groups</li>
 * <li>Pub/Sub — Redis Pub/Sub channels</li>
 * </ul>
 *
 * <p>
 * Prerequisites: Redis cluster running ({@code docker/docker-compose.yml}) and the spring-demo app running on
 * {@code localhost:8080}. Run with: {@code mvn gatling:test}
 */
public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080")
			.acceptHeader("application/json");

	private final ScenarioBuilder simple = scenario("Simple Stream")
			.exec(http("simple").post("/demo/simple").queryParam("message", "perf-test"));

	private final ScenarioBuilder work = scenario("Work Queue")
			.exec(http("work").post("/demo/work").queryParam("message", "perf-task").queryParam("count", "3"));

	private final ScenarioBuilder fanout = scenario("Fanout")
			.exec(http("fanout").post("/demo/fanout").queryParam("message", "perf-broadcast"));

	private final ScenarioBuilder pubsub = scenario("Pub/Sub")
			.exec(http("pubsub").post("/demo/pubsub").queryParam("message", "perf-broadcast"));

	{
		setUp(simple.injectOpen(atOnceUsers(10)), work.injectOpen(atOnceUsers(10)), fanout.injectOpen(atOnceUsers(10)),
				pubsub.injectOpen(atOnceUsers(10))).protocols(httpProtocol);
	}
}
