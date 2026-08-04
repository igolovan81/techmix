# WebSocket Demo

A single Spring Boot app (`spring-demo`, port `8098`) demonstrating WebSocket communication patterns over an
in-memory order-tracking domain (`CREATED → PAID → SHIPPED → DELIVERED`, plus `CANCELLED`/`FAILED` as terminal
states not reachable from this demo's REST triggers).

Unlike gRPC and Webhooks, WebSocket is inherently a single persistent connection between one client and one
server — there's no need for a second app to talk to.

## Patterns

| Pattern | Where | What it demonstrates |
|---|---|---|
| Raw WebSocket broadcast | `raw/RawOrderWebSocketHandler` | Low-level `WebSocketHandler`, manual session registry, hand-rolled fan-out |
| STOMP broadcast | `stomp/broadcast/BroadcastPublisher` | All clients subscribed to `/topic/orders` get every event |
| STOMP per-order topic | `stomp/topic/OrderTopicPublisher` | Clients subscribe only to the order they care about, `/topic/orders/{orderId}` |
| STOMP request/reply | `stomp/reqreply/OrderStatusController` | Client sends a message and gets a correlated reply on its own private queue |
| Disconnect handling / heartbeats | `disconnect/`, `config/StompConfig` | Detecting dead connections (explicit close vs. abrupt failure), STOMP heartbeats |
| Failure simulation | `util/FailureSimulator` | ~5% of raw handshakes and status-requests fail, so retry/reconnect behavior is observable |

STOMP is exposed on two endpoints sharing the same broker:
- `/ws-stomp` — SockJS-wrapped, used by the browser test client below.
- `/ws-stomp-native` — plain STOMP-over-WebSocket, used by the Gatling load test and integration tests, so they
  can speak STOMP directly without parsing the SockJS frame envelope.

## Running

```bash
cd communication-protocols
mvn -pl websockets/spring-demo spring-boot:run
```

Then open `http://localhost:8098/ws-client/index.html` in a browser.

## Walkthrough

1. Click **Create order** — its id appears next to "Order".
2. Click **Advance order** a few times, watching the STOMP broadcast panel light up automatically once connected.
3. Click **Connect** under "Raw WebSocket" and "STOMP", then advance the order again — the raw panel and STOMP
   broadcast log both show the new event.
4. Enter the order id in the topic input, click **Subscribe to order topic**, advance again — the per-order log
   line appears alongside the broadcast one.
5. Click **Request status** — the reply arrives on the private per-session queue. Occasionally (≈5% of the time)
   the request is dropped by `FailureSimulator`; click again to retry.
6. Close the tab or click **Disconnect** — the server logs the disconnect via `DisconnectEventListener` (STOMP)
   or by removing the session from `RawOrderWebSocketHandler`'s registry (raw).

## Load testing

```bash
mvn gatling:test -pl websockets/spring-demo   # requires the app running first
```

## Scope limits

- No persistence — orders live in an in-memory `ConcurrentHashMap`; a restart loses all order state and drops
  all connections.
- No authentication/authorization on WebSocket endpoints.
- No redelivery of missed events to clients that were disconnected when an event fired — a client that
  reconnects only sees events from that point forward.
- No automatic reconnect in the test client — a dropped connection is surfaced in the log and left to a manual
  reconnect button click.
- Single-instance only — no multi-node broker clustering.
