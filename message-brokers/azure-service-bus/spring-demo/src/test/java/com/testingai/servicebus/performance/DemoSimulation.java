package com.testingai.servicebus.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemoSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8082")
            .acceptHeader("application/json");

    private final ScenarioBuilder simple = scenario("Simple Queue")
            .exec(http("simple").post("/api/simple/send").queryParam("message", "perf-message"));

    private final ScenarioBuilder work = scenario("Work Queue")
            .exec(http("work").post("/api/work/send").queryParam("message", "perf-task"));

    private final ScenarioBuilder pubsub = scenario("Pub/Sub")
            .exec(http("pubsub").post("/api/pubsub/publish").queryParam("message", "perf-broadcast"));

    private final ScenarioBuilder routing = scenario("Routing")
            .exec(http("routing-info").post("/api/routing/publish")
                    .queryParam("level", "info")
                    .queryParam("message", "perf-info"))
            .exec(http("routing-error").post("/api/routing/publish")
                    .queryParam("level", "error")
                    .queryParam("message", "perf-error"));

    private final ScenarioBuilder dlq = scenario("DLQ")
            .exec(http("dlq").post("/api/dlq/send").queryParam("message", "perf-risky"));

    private final ScenarioBuilder session = scenario("Sessions")
            .exec(http("session").post("/api/session/send")
                    .queryParam("message", "perf-session")
                    .queryParam("sessionId", "perf-session-1"));

    private final ScenarioBuilder tx = scenario("Transactions")
            .exec(http("tx").post("/api/tx/send")
                    .queryParam("message", "perf-tx")
                    .queryParam("count", "3"));

    {
        setUp(
                simple.injectOpen(atOnceUsers(10)),
                work.injectOpen(atOnceUsers(10)),
                pubsub.injectOpen(atOnceUsers(10)),
                routing.injectOpen(atOnceUsers(10)),
                dlq.injectOpen(atOnceUsers(10)),
                session.injectOpen(atOnceUsers(10)),
                tx.injectOpen(atOnceUsers(10))
        ).protocols(httpProtocol)
         .maxDuration(Duration.ofMinutes(2));
    }
}
