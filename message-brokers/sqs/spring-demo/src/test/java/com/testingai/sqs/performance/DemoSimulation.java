package com.testingai.sqs.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling load simulation for the SQS spring-demo.
 *
 * <p>
 * Exercises four messaging patterns in parallel:
 * <ul>
 * <li>Simple Queue — basic SQS send/receive</li>
 * <li>Work Queue — competing consumers across multiple SQS workers</li>
 * <li>Fanout — SNS topic fanning out to multiple SQS queues</li>
 * <li>Pub/Sub — SNS-to-SQS event distribution</li>
 * </ul>
 *
 * <p>
 * Prerequisites: LocalStack running ({@code docker/docker-compose.yml}) and the spring-demo app running on
 * {@code localhost:8081}. Run with: {@code mvn gatling:test}
 */
public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8081")
			.acceptHeader("application/json");

	private final ScenarioBuilder simple = scenario("Simple Queue")
			.exec(http("simple").post("/demo/simple").queryParam("message", "perf-test"));

	private final ScenarioBuilder work = scenario("Work Queue")
			.exec(http("work").post("/demo/work").queryParam("message", "perf-task").queryParam("count", "3"));

	private final ScenarioBuilder fanout = scenario("Fanout")
			.exec(http("fanout").post("/demo/fanout").queryParam("message", "perf-broadcast"));

	private final ScenarioBuilder pubsub = scenario("Pub/Sub")
			.exec(http("pubsub").post("/demo/pubsub").queryParam("message", "perf-event"));

	{
		setUp(simple.injectOpen(atOnceUsers(10)), work.injectOpen(atOnceUsers(10)), fanout.injectOpen(atOnceUsers(10)),
				pubsub.injectOpen(atOnceUsers(10))).protocols(httpProtocol);
	}
}
