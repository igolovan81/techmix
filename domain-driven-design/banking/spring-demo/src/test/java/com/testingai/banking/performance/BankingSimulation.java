package com.testingai.banking.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class BankingSimulation extends Simulation {

	private static final String OPEN_ACCOUNT_BODY = """
			{"ownerName":"Load Test User","initialBalance":1000.00,"currency":"USD"}""";

	private static final String DEPOSIT_BODY = """
			{"amount":50.00,"currency":"USD"}""";

	private static final String WITHDRAW_BODY = """
			{"amount":20.00,"currency":"USD"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8099")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder ledgerScenario = scenario("Ledger Operations")
			.exec(http("Open Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY)).check(status().is(201))
					.check(jsonPath("$.accountId").saveAs("accountId")))
			.exec(http("Deposit").post("/accounts/#{accountId}/deposits").body(StringBody(DEPOSIT_BODY))
					.check(status().is(200)))
			.exec(http("Withdraw").post("/accounts/#{accountId}/withdrawals").body(StringBody(WITHDRAW_BODY))
					.check(status().is(200)))
			.exec(http("Get Account").get("/accounts/#{accountId}").check(status().is(200)))
			.exec(http("Get Statement").get("/accounts/#{accountId}/statement").check(status().is(200)));

	private final ScenarioBuilder transferScenario = scenario("Transfers")
			.exec(http("Open Source Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY))
					.check(status().is(201)).check(jsonPath("$.accountId").saveAs("fromAccountId")))
			.exec(http("Open Target Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY))
					.check(status().is(201)).check(jsonPath("$.accountId").saveAs("toAccountId")))
			.exec(http("Transfer").post("/transfers").body(StringBody(
					"{\"fromAccountId\":\"#{fromAccountId}\",\"toAccountId\":\"#{toAccountId}\",\"amount\":10.00,\"currency\":\"USD\"}"))
					.check(status().is(200)));

	{
		setUp(ledgerScenario.injectOpen(atOnceUsers(10)), transferScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
