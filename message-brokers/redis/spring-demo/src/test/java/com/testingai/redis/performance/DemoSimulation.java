package com.testingai.redis.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemoSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json");

    private final ScenarioBuilder simple = scenario("Simple Stream")
            .exec(http("simple").post("/demo/simple").queryParam("message", "perf-test"));

    private final ScenarioBuilder work = scenario("Work Queue")
            .exec(http("work").post("/demo/work")
                    .queryParam("message", "perf-task")
                    .queryParam("count", "3"));

    private final ScenarioBuilder fanout = scenario("Fanout")
            .exec(http("fanout").post("/demo/fanout").queryParam("message", "perf-broadcast"));

    private final ScenarioBuilder pubsub = scenario("Pub/Sub")
            .exec(http("pubsub").post("/demo/pubsub").queryParam("message", "perf-broadcast"));

    {
        setUp(
                simple.injectOpen(atOnceUsers(10)),
                work.injectOpen(atOnceUsers(10)),
                fanout.injectOpen(atOnceUsers(10)),
                pubsub.injectOpen(atOnceUsers(10))
        ).protocols(httpProtocol);
    }
}
