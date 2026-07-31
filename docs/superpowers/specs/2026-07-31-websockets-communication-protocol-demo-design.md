# WebSocket Communication Protocol Demo Design

**Date:** 2026-07-31
**Status:** Approved

## Overview

A new `websockets/` module under `communication-protocols/` (sibling to `grpc/`, `graphql/`, `webhooks/`), containing a **single** Spring Boot app — `spring-demo` — mirroring the GraphQL demo's topology. Unlike webhooks (inherently two-sided), a WebSocket demo only needs one process holding the connections; splitting it into two apps would add coordination overhead without demonstrating anything new.

The domain is order lifecycle tracking (`CREATED → PAID → SHIPPED → DELIVERED`, plus `CANCELLED`/`FAILED`) — its own independent in-memory model, not shared code with `distributed-transactions/saga`, `workflow-engines/camunda`, or `webhooks`, chosen only because the name/shape is already familiar across this repo.

No external infrastructure is required (no Docker) — the app runs locally via `mvn spring-boot:run`, like `grpc/`, `graphql/`, and `webhooks/`.

## Patterns implemented (parallels the GraphQL/webhooks README pattern tables)

| Pattern | Where | What it demonstrates |
|---|---|---|
| Raw WebSocket broadcast | `raw/RawOrderWebSocketHandler` | Low-level `WebSocketHandler`, manual session registry, hand-rolled fan-out — the wire-level mental model before STOMP framing is introduced |
| STOMP broadcast | `stomp/broadcast/BroadcastPublisher` | Spring's higher-level messaging: all clients subscribed to one destination (`/topic/orders`) get every event |
| STOMP per-destination topic | `stomp/topic/OrderTopicPublisher` | Clients subscribe only to the specific resource they care about (`/topic/orders/{orderId}`) — selective fan-out |
| STOMP request/reply | `stomp/reqreply/OrderStatusController` | Client sends a message and gets a correlated reply on its own private queue (`@MessageMapping` + `convertAndSendToUser`) |
| Disconnect handling / heartbeats | `disconnect/` | Detecting and cleaning up dead connections (explicit close vs. abrupt/transport failure), STOMP heartbeats |
| Failure simulation | `util/FailureSimulator` | ~5% of connection attempts / requests fail, so the test client's retry/reconnect behavior is observable on demand |

## Repository structure

```
communication-protocols/
├── README.md                                          (add WebSocket row to the protocol table)
└── websockets/
    ├── README.md                                      (protocol overview, pattern table, running instructions, walkthrough)
    └── spring-demo/
        ├── pom.xml                                    (artifactId: websockets-spring-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/websockets/
            │   │   ├── WebSocketsSpringDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── Order.java                       (record: id, status, updatedAt)
            │   │   │   ├── OrderStatus.java                  (enum: CREATED, PAID, SHIPPED, DELIVERED, CANCELLED, FAILED)
            │   │   │   ├── OrderEvent.java                   (record: orderId, status, occurredAt — the payload pushed over every channel)
            │   │   │   ├── NoNextStatusException.java        (thrown by advance() on a terminal-status order; mapped to HTTP 409 by DemoController)
            │   │   │   └── OrderTrackingService.java         (in-memory ConcurrentHashMap<String, Order>; advance() computes next status —
            │   │   │                                          throwing NoNextStatusException if the order is already DELIVERED, CANCELLED, or
            │   │   │                                          FAILED — and invokes all three publishers: raw registry broadcast, STOMP
            │   │   │                                          broadcast, STOMP topic)
            │   │   ├── controller/
            │   │   │   └── DemoController.java               (REST: POST /api/orders, POST /api/orders/{id}/advance)
            │   │   ├── raw/
            │   │   │   ├── RawOrderWebSocketHandler.java      (TextWebSocketHandler; ConcurrentHashMap<String, WebSocketSession> registry;
            │   │   │   │                                       afterConnectionEstablished calls FailureSimulator.maybeThrow to reject ~5% of
            │   │   │   │                                       handshakes; broadcast(OrderEvent) sends JSON to every open session)
            │   │   │   └── RawWebSocketConfig.java            (WebSocketConfigurer; registers handler at /ws/raw/orders)
            │   │   ├── stomp/
            │   │   │   ├── broadcast/
            │   │   │   │   └── BroadcastPublisher.java        (convertAndSend("/topic/orders", event))
            │   │   │   ├── topic/
            │   │   │   │   └── OrderTopicPublisher.java       (convertAndSend("/topic/orders/" + orderId, event))
            │   │   │   └── reqreply/
            │   │   │       └── OrderStatusController.java     (@MessageMapping("/orders/{id}/status-request"); calls
            │   │   │                                           FailureSimulator.maybeThrow(context) before replying; replies via
            │   │   │                                           convertAndSendToUser(sessionId, "/queue/orders/" + id + "/status", event))
            │   │   ├── disconnect/
            │   │   │   └── DisconnectEventListener.java       (@EventListener on SessionDisconnectEvent for STOMP; RawOrderWebSocketHandler's
            │   │   │                                           own afterConnectionClosed for the raw registry — both log normal vs. abrupt
            │   │   │                                           closure and remove the session from its registry)
            │   │   ├── config/
            │   │   │   └── StompConfig.java                   (@EnableWebSocketMessageBroker; registers two STOMP endpoints on the same
            │   │   │                                           broker — /ws-stomp with SockJS fallback for the browser test client, and
            │   │   │                                           /ws-stomp-native as plain STOMP-over-WebSocket (no SockJS envelope) for
            │   │   │                                           Gatling and the Spring integration tests, avoiding the need to hand-roll
            │   │   │                                           SockJS frame parsing in test code; enableSimpleBroker("/topic", "/queue")
            │   │   │                                           with heartbeat[10000,10000] and a TaskScheduler bean for heartbeat delivery;
            │   │   │                                           setApplicationDestinationPrefixes("/app"))
            │   │   └── util/
            │   │       └── FailureSimulator.java              (same shape as every other module: FAILURE_RATE = 0.05, maybeThrow(String context))
            │   └── resources/
            │       ├── application.yml                        (server.port: 8098)
            │       └── static/ws-client/
            │           └── index.html                         (vanilla JS test page — see "Test client" below; sockjs-client + stompjs webjars)
            └── test/
                ├── java/com/testingai/websockets/
                │   ├── WebSocketsSpringDemoApplicationTest.java
                │   ├── domain/OrderTrackingServiceTest.java
                │   ├── util/FailureSimulatorTest.java
                │   ├── raw/RawOrderWebSocketHandlerTest.java        (unit: registry add/remove/broadcast with mocked WebSocketSession)
                │   ├── stomp/broadcast/BroadcastPublisherTest.java  (unit: verifies convertAndSend called with expected destination/payload)
                │   ├── stomp/topic/OrderTopicPublisherTest.java
                │   ├── stomp/reqreply/OrderStatusControllerTest.java
                │   ├── controller/
                │   │   ├── DemoControllerTest.java                 (MockMvc: create/advance endpoints)
                │   │   ├── RawWebSocketIntegrationTest.java        (@SpringBootTest RANDOM_PORT; raw WebSocketClient connects to
                │   │   │                                            /ws/raw/orders, triggers advance() via REST, asserts the broadcast
                │   │   │                                            frame arrives; repeats connect attempts to observe the ~5% reject rate
                │   │   │                                            is bounded, i.e. not every attempt fails)
                │   │   └── StompIntegrationTest.java               (@SpringBootTest RANDOM_PORT; WebSocketStompClient connects to
                │   │                                                /ws-stomp, subscribes to /topic/orders, /topic/orders/{id}, sends a
                │   │                                                status-request and asserts the reply on /user/queue/orders/{id}/status)
                │   └── disconnect/DisconnectEventListenerTest.java (unit: publishing a SessionDisconnectEvent removes the session id
                │                                                    from a stubbed registry)
                └── performance/DemoSimulation.java                 (Gatling — WebSocket DSL; opens raw + STOMP connections, drives
                                                                      REST advance() calls concurrently, asserts frames are received;
                                                                      excluded from `mvn test` via the inherited surefire
                                                                      `**/performance/**` exclude, run explicitly with `mvn gatling:test`)
```

### Cross-cutting fixes needed in existing files

- **`CLAUDE.md`** — add a "WebSocket communication protocol demo" command section (single app, run from the reactor root, no Docker) and a row for the new module in the repository layout table.
- **`communication-protocols/pom.xml`** — add `websockets/spring-demo` to `<modules>`.
- **`communication-protocols/README.md`** — add a WebSocket row to the protocol table; remove the "(e.g. WebSocket)" parenthetical from the closing sentence since it's no longer a hypothetical future addition.
- **`.githooks/pre-commit`** — already greps `^communication-protocols/.*\.java$` and runs `mvn spotless:apply` there; no change needed, the new module is already covered by the path pattern.

## Order tracking & fan-out flow

1. User starts `spring-demo` (port 8098) and opens `http://localhost:8098/ws-client/` in a browser.
2. The test client opens three kinds of connections: a raw WebSocket to `/ws/raw/orders`, and a STOMP/SockJS connection to `/ws-stomp` from which it subscribes to `/topic/orders` (broadcast panel) and, once an order id is entered, `/topic/orders/{id}` (per-order panel).
3. User creates an order: `POST /api/orders` → `OrderTrackingService` stores it as `CREATED` and returns its id.
4. User advances it: `POST /api/orders/{id}/advance` → `OrderTrackingService` computes the next `OrderStatus`, builds an `OrderEvent`, and hands it to all three publishers:
   - `RawOrderWebSocketHandler.broadcast(event)` — sends the JSON frame to every session in its registry
   - `BroadcastPublisher.publish(event)` — `/topic/orders`
   - `OrderTopicPublisher.publish(event)` — `/topic/orders/{id}`
5. User triggers a request/reply: from the test client's reqreply panel, sends a STOMP message to `/app/orders/{id}/status-request`; `OrderStatusController` looks up the current order, calls `FailureSimulator.maybeThrow("status-request")` (may throw, causing the client to see an error frame instead of a reply — demonstrating the simulated-failure path), and on success replies to the sender's private queue with the current `OrderEvent`.
6. When a browser tab closes or a connection drops: for the raw handler, `afterConnectionClosed` removes the session from its registry directly; for STOMP, `DisconnectEventListener` reacts to `SessionDisconnectEvent` and logs/removes any per-session bookkeeping. STOMP heartbeats (`heartbeat[10000,10000]`) mean a network-level dead connection is detected within ~10s even without a clean close frame.

## Ports

`8098` — next free slot after `8097` (`communication-protocols/webhooks/consumer-demo`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from the parent POM)
**Java:** 21

**Dependencies:** `spring-boot-starter-web`, `spring-boot-starter-websocket`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test), `org.webjars:sockjs-client`, `org.webjars:stomp-websocket` (for the static test client, served by Spring's default static-resource handling — no separate frontend build).

No Spring Security — this module has no auth-sensitive surface (unlike GraphQL's role-based field access), so it's left out entirely rather than added as boilerplate.

## Test client

`src/main/resources/static/ws-client/index.html`, served at `http://localhost:8098/ws-client/`. A single vanilla-JS page (no build step) with:
- **Raw panel** — connect/disconnect buttons for `/ws/raw/orders`, a live log of received frames
- **STOMP broadcast panel** — subscribes to `/topic/orders` on connect
- **STOMP per-order topic panel** — order-id input, subscribes to `/topic/orders/{id}`
- **Request/reply panel** — order-id input, a "request status" button that sends to `/app/orders/{id}/status-request` and logs the reply from `/user/queue/orders/{id}/status`
- **REST trigger controls** — buttons for `POST /api/orders` and `POST /api/orders/{id}/advance`, so the full loop (REST trigger → WebSocket push → client log) is visible on one page
- **Connection log** — a shared log area showing connect/disconnect/heartbeat/error events, making the ~5% simulated failures and any reconnect attempts visible

## Testing

- **`OrderTrackingServiceTest`** — create, advance through the full status sequence, advancing a terminal-state (`DELIVERED`/`CANCELLED`/`FAILED`) order throws `NoNextStatusException`.
- **`FailureSimulatorTest`** — same convention as every other module (statistical assertion over many calls that failures occur roughly at the configured rate, or a fixed-seed/mocked-random check per the Kafka reference).
- **`RawOrderWebSocketHandlerTest`** — registers a mocked `WebSocketSession`, asserts `broadcast()` sends to it; asserts a closed session is removed and skipped on subsequent broadcasts.
- **`BroadcastPublisherTest` / `OrderTopicPublisherTest` / `OrderStatusControllerTest`** — verify `SimpMessagingTemplate` is invoked with the expected destination and payload (Mockito).
- **`DemoControllerTest`** (MockMvc) — create/advance endpoints return the expected order state.
- **`RawWebSocketIntegrationTest`** (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) — a real `WebSocketClient` connects, an `advance()` REST call is made, the client asserts it receives the broadcast frame; also opens several connections in a loop and asserts the reject rate stays bounded (not 0%, not 100%) to exercise `FailureSimulator` on the handshake path.
- **`StompIntegrationTest`** (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) — `WebSocketStompClient` connects to `/ws-stomp`, subscribes to `/topic/orders` and `/topic/orders/{id}`, asserts both receive an event after `advance()`; sends a status-request and asserts the reply arrives on the private queue.
- **`DisconnectEventListenerTest`** — publishing a `SessionDisconnectEvent` triggers cleanup of the corresponding registry entry.
- **`performance/DemoSimulation.java`** (Gatling, WebSocket DSL) — opens a pool of raw + STOMP connections (STOMP via `/ws-stomp-native`, sending hand-built `CONNECT`/`SUBSCRIBE` STOMP frames directly — no SockJS envelope to parse), drives concurrent `advance()` calls via REST, asserts expected frames are received; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly with `mvn gatling:test`.
- **No JMeter** for this module — JMeter has no first-party WebSocket sampler (the available option is an unofficial third-party plugin jar), whereas Gatling has native WebSocket DSL support already wired into every other module via `gatling:test`. Skipping JMeter here avoids taking on an unofficial plugin dependency purely for test-coverage-parity's sake.

## README

- `communication-protocols/README.md` — add a WebSocket row ("Single Spring Boot app — raw WebSocket + STOMP broadcast/topic/request-reply, with a static browser test client"); drop the "(e.g. WebSocket)" parenthetical from the closing sentence.
- `communication-protocols/websockets/README.md` — protocol-level overview (what WebSocket is: a persistent full-duplex connection, contrasted with the request/response style of gRPC/GraphQL and the server-push-without-a-standing-connection style of webhooks), the pattern table above, prerequisites, run instructions, and a walkthrough:
  1. start the app, open `/ws-client/`
  2. create an order, watch its id appear
  3. click advance a few times, watch the raw panel, broadcast panel, and (after entering the order id) the per-order topic panel all light up
  4. enter the order id in the request/reply panel, click "request status", observe the reply arrive on the private queue
  5. reconnect a few times to observe the ~5% simulated handshake failures and request failures in the connection log
  6. close the tab / kill the connection, observe the server log a disconnect via `DisconnectEventListener`

## Scope limits

- No persistence — orders live in an in-memory `ConcurrentHashMap`, matching every other module in this repo. A restart loses all order state and drops all connections.
- No authentication/authorization on WebSocket endpoints — this module's focus is the protocol patterns themselves, not securing them (unlike GraphQL, which has a dedicated security-roles design).
- No message ordering/delivery guarantees beyond what the simple in-memory STOMP broker and raw handler naturally provide — no persistent queue, no redelivery of missed events to a client that was disconnected when an event fired. A client that reconnects only sees events from that point forward.
- Heartbeat/reconnect logic lives on the server (heartbeat interval) and is *observable* in the test client's log, but the test client does not implement automatic reconnect-on-failure — it surfaces the failure and leaves reconnection to a manual button click, keeping the vanilla-JS client simple.
- Single-instance only — no multi-node session/broker clustering (e.g. no external STOMP relay broker like RabbitMQ), consistent with this module's "no external infrastructure" goal.
