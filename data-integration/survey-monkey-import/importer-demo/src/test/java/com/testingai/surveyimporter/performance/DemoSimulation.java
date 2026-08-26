package com.testingai.surveyimporter.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8102")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder importScenario = scenario("Survey Import")
			.exec(http("Trigger Sync — survey-1").post("/demo/surveys/survey-1/sync").check(status().is(202)))
			.exec(http("Trigger Sync — survey-2").post("/demo/surveys/survey-2/sync").check(status().is(202)))
			.exec(http("Status").get("/demo/status").check(status().is(200)));

	{
		setUp(importScenario.injectOpen(atOnceUsers(5))).protocols(httpProtocol).maxDuration(Duration.ofSeconds(60));
	}
}
