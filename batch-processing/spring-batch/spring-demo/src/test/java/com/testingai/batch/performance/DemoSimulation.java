package com.testingai.batch.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8103")
			.acceptHeader("application/json").contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("Spring Batch Demo")
			.exec(http("Seed Chunk Orders").post("/demo/orders/seed?type=CHUNK&count=20").check(status().is(200)))
			.exec(http("Seed FaultTolerant Orders").post("/demo/orders/seed?type=FAULT_TOLERANT&count=20")
					.check(status().is(200)))
			.exec(http("Seed Partition Orders").post("/demo/orders/seed?type=PARTITION&count=20")
					.check(status().is(200)))
			.exec(http("Launch Chunk Job").post("/demo/batch/chunk").check(status().is(200)))
			.exec(http("Listener Stats").get("/demo/batch/listener-stats").check(status().is(200)))
			.exec(http("Launch Tasklet Job").post("/demo/batch/tasklet").check(status().is(200)))
			.exec(http("Launch FaultTolerant Job").post("/demo/batch/fault-tolerant").check(status().is(200)))
			.exec(http("Launch Partition Job").post("/demo/batch/partition").check(status().is(200)))
			.exec(http("List Invoices").get("/demo/invoices").check(status().is(200)));

	{
		setUp(demoScenario.injectOpen(atOnceUsers(5))).protocols(httpProtocol);
	}
}
