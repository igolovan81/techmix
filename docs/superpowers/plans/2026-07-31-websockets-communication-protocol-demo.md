# WebSocket Communication Protocol Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `communication-protocols/websockets/spring-demo` module demonstrating raw WebSocket broadcast, STOMP broadcast/per-order-topic/request-reply patterns, disconnect handling, and simulated failures over an in-memory order-tracking domain, with a static browser test client.

**Architecture:** Single Spring Boot 3.4.4 app on port 8098. An `OrderTrackingService` holds order state and fans out every status change to a list of `OrderEventPublisher` beans (raw handler, STOMP broadcast, STOMP per-order topic) via Spring's list-autowiring. STOMP is exposed on two endpoints on the same broker: `/ws-stomp` (SockJS-wrapped, for the browser client) and `/ws-stomp-native` (plain STOMP-over-WebSocket, for Gatling and integration tests, avoiding SockJS frame parsing in test code).

**Tech Stack:** Spring Boot 3.4.4, Java 21, `spring-boot-starter-websocket` (raw `WebSocketHandler` + STOMP/`SimpMessagingTemplate`), Jackson (records), JUnit 5, Mockito, AssertJ, Gatling.

## Global Constraints

- Java 21 / Spring Boot 3.4.4, inherited from `communication-protocols/pom.xml` — do not override.
- No Docker / external infrastructure — the app runs standalone via `mvn spring-boot:run`.
- All state is in-memory (`ConcurrentHashMap`) — no persistence.
- Port: `8098` (next free slot after `8097`, `webhooks/consumer-demo`).
- No Spring Security in this module — no auth-sensitive surface to protect.
- No JMeter for this module — Gatling only (no first-party JMeter WebSocket sampler; see spec's rationale).
- Package root: `com.testingai.websockets`.
- `communication-protocols/**.java` is already covered by `.githooks/pre-commit`'s Spotless auto-format — no build changes needed there, and do not hand-format; the hook does it automatically on commit.
- `FailureSimulator` must follow the Kafka reference exactly: `FAILURE_RATE = 0.05`, `maybeThrow(String context)`, no `shouldFail()` boolean method.
- STOMP is registered on two endpoints on the same broker: `/ws-stomp` (`.withSockJS()`, for the browser test client) and `/ws-stomp-native` (plain, for Gatling/integration tests) — see spec addendum dated 2026-07-31.
- Order status sequence: `CREATED → PAID → SHIPPED → DELIVERED`, with `CANCELLED`/`FAILED` as additional terminal states not reachable via `advance()` in this demo (no cancel/fail endpoints are in scope — only the happy-path sequence is driven by `advance()`).
- Reference spec: `docs/superpowers/specs/2026-07-31-websockets-communication-protocol-demo-design.md`.

---

## File Structure

```
communication-protocols/
├── README.md                                              (edit: add WebSocket row)
├── pom.xml                                                 (edit: add websockets/spring-demo module)
└── websockets/
    ├── README.md                                          (new)
    └── spring-demo/
        ├── pom.xml
        └── src/
            ├── main/java/com/testingai/websockets/
            │   ├── WebSocketsSpringDemoApplication.java
            │   ├── domain/
            │   │   ├── OrderStatus.java
            │   │   ├── Order.java
            │   │   ├── OrderEvent.java
            │   │   ├── OrderEventPublisher.java
            │   │   ├── OrderNotFoundException.java
            │   │   ├── NoNextStatusException.java
            │   │   └── OrderTrackingService.java
            │   ├── util/FailureSimulator.java
            │   ├── controller/DemoController.java
            │   ├── raw/
            │   │   ├── RawOrderWebSocketHandler.java
            │   │   ├── FailureSimulatingHandshakeInterceptor.java
            │   │   └── RawWebSocketConfig.java
            │   ├── config/StompConfig.java
            │   ├── stomp/
            │   │   ├── broadcast/BroadcastPublisher.java
            │   │   ├── topic/OrderTopicPublisher.java
            │   │   └── reqreply/OrderStatusController.java
            │   ├── disconnect/DisconnectEventListener.java
            │   └── resources → src/main/resources/
            ├── main/resources/
            │   ├── application.yml
            │   └── static/ws-client/index.html
            └── test/java/com/testingai/websockets/
                ├── WebSocketsSpringDemoApplicationTest.java
                ├── WsClientStaticResourceTest.java
                ├── domain/OrderTrackingServiceTest.java
                ├── util/FailureSimulatorTest.java
                ├── controller/
                │   ├── DemoControllerTest.java
                │   ├── RawWebSocketIntegrationTest.java
                │   └── StompIntegrationTest.java
                ├── raw/
                │   ├── RawOrderWebSocketHandlerTest.java
                │   └── FailureSimulatingHandshakeInterceptorTest.java
                ├── stomp/
                │   ├── broadcast/BroadcastPublisherTest.java
                │   ├── topic/OrderTopicPublisherTest.java
                │   └── reqreply/OrderStatusControllerTest.java
                ├── disconnect/DisconnectEventListenerTest.java
                └── performance/DemoSimulation.java
```

Cross-cutting doc edits: `communication-protocols/README.md`, `CLAUDE.md` (root), both handled in Task 13.

---

### Task 1: Module scaffold

**Files:**
- Create: `communication-protocols/websockets/spring-demo/pom.xml`
- Modify: `communication-protocols/pom.xml` (add module)
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/WebSocketsSpringDemoApplication.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/resources/application.yml`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/WebSocketsSpringDemoApplicationTest.java`

**Interfaces:**
- Produces: a buildable, testable Spring Boot module on port 8098. No production classes yet beyond the application entry point.

- [ ] **Step 1: Add the module to the parent POM**

Edit `communication-protocols/pom.xml`, inside `<modules>`, add after the webhooks entries:

```xml
        <module>websockets/spring-demo</module>
```

- [ ] **Step 2: Create the module POM**

`communication-protocols/websockets/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>communication-protocols</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>websockets-spring-demo</artifactId>
    <name>WebSocket Spring Demo</name>
    <description>Raw WebSocket and STOMP broadcast/topic/request-reply patterns over an order-tracking domain</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.webjars</groupId>
            <artifactId>sockjs-client</artifactId>
            <version>1.5.1</version>
        </dependency>
        <dependency>
            <groupId>org.webjars</groupId>
            <artifactId>stomp-websocket</artifactId>
            <version>2.3.4</version>
        </dependency>
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>${gatling.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.websockets.WebSocketsSpringDemoApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <configuration>
                    <simulationClass>com.testingai.websockets.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create the application class**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/WebSocketsSpringDemoApplication.java`:

```java
package com.testingai.websockets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebSocketsSpringDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebSocketsSpringDemoApplication.class, args);
	}
}
```

- [ ] **Step 4: Create application.yml**

`communication-protocols/websockets/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8098
```

- [ ] **Step 5: Write the application context test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/WebSocketsSpringDemoApplicationTest.java`:

```java
package com.testingai.websockets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebSocketsSpringDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 6: Build and run the test**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo -am test`
Expected: BUILD SUCCESS, 1 test run (`contextLoads`).

- [ ] **Step 7: Commit**

```bash
git add communication-protocols/pom.xml communication-protocols/websockets/spring-demo/pom.xml communication-protocols/websockets/spring-demo/src
git commit -m "feat(communication-protocols): scaffold websockets-spring-demo module"
```

---

### Task 2: Order domain model + OrderTrackingService

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderStatus.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/Order.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderEvent.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderEventPublisher.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderNotFoundException.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/NoNextStatusException.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderTrackingService.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/domain/OrderTrackingServiceTest.java`

**Interfaces:**
- Produces:
  - `record Order(String id, OrderStatus status, java.time.Instant updatedAt)` with `Order withStatus(OrderStatus newStatus, Instant at)`
  - `record OrderEvent(String orderId, OrderStatus status, java.time.Instant occurredAt)`
  - `enum OrderStatus { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED, FAILED }`
  - `interface OrderEventPublisher { void publish(OrderEvent event); }` — later tasks' publishers (raw handler, STOMP broadcast, STOMP topic) implement this; Spring autowires all beans of this type into `OrderTrackingService`'s constructor as a `List`.
  - `class OrderTrackingService` with `Order create()`, `Order advance(String orderId)` (throws `OrderNotFoundException` if unknown, `NoNextStatusException` if already `DELIVERED`/`CANCELLED`/`FAILED`), `Order get(String orderId)` (throws `OrderNotFoundException` if unknown)
  - `OrderNotFoundException` — `@ResponseStatus(HttpStatus.NOT_FOUND)`
  - `NoNextStatusException` — `@ResponseStatus(HttpStatus.CONFLICT)`

- [ ] **Step 1: Write the failing test for OrderTrackingService**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/domain/OrderTrackingServiceTest.java`:

```java
package com.testingai.websockets.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTrackingServiceTest {

	private final List<OrderEvent> published = new ArrayList<>();
	private final OrderTrackingService service = new OrderTrackingService(List.of(published::add));

	@Test
	void create_returnsOrderInCreatedStatus() {
		Order order = service.create();

		assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void advance_movesThroughFullStatusSequence() {
		Order order = service.create();

		Order paid = service.advance(order.id());
		Order shipped = service.advance(order.id());
		Order delivered = service.advance(order.id());

		assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
		assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
	}

	@Test
	void advance_publishesEventToEveryPublisher_onEachTransition() {
		Order order = service.create();

		service.advance(order.id());

		assertThat(published).hasSize(1);
		assertThat(published.get(0).orderId()).isEqualTo(order.id());
		assertThat(published.get(0).status()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void advance_throwsNoNextStatusException_whenOrderAlreadyDelivered() {
		Order order = service.create();
		service.advance(order.id());
		service.advance(order.id());
		service.advance(order.id());

		assertThatThrownBy(() -> service.advance(order.id())).isInstanceOf(NoNextStatusException.class);
	}

	@Test
	void advance_throwsOrderNotFoundException_whenOrderUnknown() {
		assertThatThrownBy(() -> service.advance("unknown")).isInstanceOf(OrderNotFoundException.class);
	}

	@Test
	void get_returnsCurrentOrderState() {
		Order order = service.create();

		assertThat(service.get(order.id())).isEqualTo(order);
	}

	@Test
	void get_throwsOrderNotFoundException_whenOrderUnknown() {
		assertThatThrownBy(() -> service.get("unknown")).isInstanceOf(OrderNotFoundException.class);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderTrackingServiceTest`
Expected: FAIL (compile error — `OrderTrackingService` and friends don't exist yet)

- [ ] **Step 3: Create the domain types**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderStatus.java`:

```java
package com.testingai.websockets.domain;

public enum OrderStatus {
	CREATED, PAID, SHIPPED, DELIVERED, CANCELLED, FAILED
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/Order.java`:

```java
package com.testingai.websockets.domain;

import java.time.Instant;

public record Order(String id, OrderStatus status, Instant updatedAt) {

	public Order withStatus(OrderStatus newStatus, Instant at) {
		return new Order(id, newStatus, at);
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderEvent.java`:

```java
package com.testingai.websockets.domain;

import java.time.Instant;

public record OrderEvent(String orderId, OrderStatus status, Instant occurredAt) {
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderEventPublisher.java`:

```java
package com.testingai.websockets.domain;

@FunctionalInterface
public interface OrderEventPublisher {

	void publish(OrderEvent event);
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderNotFoundException.java`:

```java
package com.testingai.websockets.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException(String orderId) {
		super("Unknown order: " + orderId);
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/NoNextStatusException.java`:

```java
package com.testingai.websockets.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NoNextStatusException extends RuntimeException {

	public NoNextStatusException(String orderId, OrderStatus currentStatus) {
		super("Order " + orderId + " has no next status from terminal state " + currentStatus);
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain/OrderTrackingService.java`:

```java
package com.testingai.websockets.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderTrackingService {

	private static final Map<OrderStatus, OrderStatus> NEXT_STATUS = Map.of(OrderStatus.CREATED, OrderStatus.PAID,
			OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

	private final Map<String, Order> orders = new ConcurrentHashMap<>();
	private final List<OrderEventPublisher> publishers;

	public OrderTrackingService(List<OrderEventPublisher> publishers) {
		this.publishers = publishers;
	}

	public Order create() {
		Order order = new Order(UUID.randomUUID().toString(), OrderStatus.CREATED, Instant.now());
		orders.put(order.id(), order);
		return order;
	}

	public Order advance(String orderId) {
		Order current = get(orderId);
		OrderStatus next = NEXT_STATUS.get(current.status());
		if (next == null) {
			throw new NoNextStatusException(orderId, current.status());
		}
		Order updated = current.withStatus(next, Instant.now());
		orders.put(orderId, updated);
		publishers.forEach(publisher -> publisher.publish(new OrderEvent(orderId, next, updated.updatedAt())));
		return updated;
	}

	public Order get(String orderId) {
		Order order = orders.get(orderId);
		if (order == null) {
			throw new OrderNotFoundException(orderId);
		}
		return order;
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderTrackingServiceTest`
Expected: PASS, 7 tests

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/domain communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/domain
git commit -m "feat(communication-protocols): add order tracking domain model for websockets demo"
```

---

### Task 3: FailureSimulator

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/util/FailureSimulator.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/util/FailureSimulatorTest.java`

**Interfaces:**
- Produces: `FailureSimulator.maybeThrow(String context)` — static, throws `RuntimeException` ~5% of calls.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/util/FailureSimulatorTest.java`:

```java
package com.testingai.websockets.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

	@Test
	void maybeThrow_doesNotThrowMostOfTheTime() {
		int failures = 0;
		for (int i = 0; i < 1000; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException e) {
				failures++;
			}
		}
		// With 5% failure rate, expect roughly 50 failures; accept 5-200 range
		assertThat(failures).isBetween(5, 200);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=FailureSimulatorTest`
Expected: FAIL (compile error — `FailureSimulator` doesn't exist yet)

- [ ] **Step 3: Create FailureSimulator**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/util/FailureSimulator.java`:

```java
package com.testingai.websockets.util;

public class FailureSimulator {

	private static final double FAILURE_RATE = 0.05;

	private FailureSimulator() {
	}

	public static void maybeThrow(String context) {
		if (Math.random() < FAILURE_RATE) {
			throw new RuntimeException("Simulated 5% failure in " + context);
		}
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=FailureSimulatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/util communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/util
git commit -m "feat(communication-protocols): add FailureSimulator for websockets demo"
```

---

### Task 4: DemoController (REST triggers)

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/controller/DemoController.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `OrderTrackingService.create()`, `.advance(String)` (Task 2)
- Produces: `POST /api/orders` → 200 + `Order` JSON body; `POST /api/orders/{id}/advance` → 200 + `Order`, 404 if unknown, 409 if terminal.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/DemoControllerTest.java`:

```java
package com.testingai.websockets.controller;

import com.testingai.websockets.domain.OrderTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoControllerTest {

	private final OrderTrackingService orderTrackingService = new OrderTrackingService(List.of());
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(orderTrackingService)).build();

	@Test
	void createOrder_returnsNewOrderInCreatedStatus() throws Exception {
		mockMvc.perform(post("/api/orders")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREATED"));
	}

	@Test
	void advanceOrder_movesToNextStatus() throws Exception {
		String orderId = orderTrackingService.create().id();

		mockMvc.perform(post("/api/orders/{id}/advance", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
	}

	@Test
	void advanceOrder_returns409_whenOrderIsInTerminalStatus() throws Exception {
		String orderId = orderTrackingService.create().id();
		orderTrackingService.advance(orderId);
		orderTrackingService.advance(orderId);
		orderTrackingService.advance(orderId);

		mockMvc.perform(post("/api/orders/{id}/advance", orderId)).andExpect(status().isConflict());
	}

	@Test
	void advanceOrder_returns404_whenOrderUnknown() throws Exception {
		mockMvc.perform(post("/api/orders/{id}/advance", "unknown")).andExpect(status().isNotFound());
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=DemoControllerTest`
Expected: FAIL (compile error — `DemoController` doesn't exist yet)

- [ ] **Step 3: Create DemoController**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/controller/DemoController.java`:

```java
package com.testingai.websockets.controller;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

	private final OrderTrackingService orderTrackingService;

	public DemoController(OrderTrackingService orderTrackingService) {
		this.orderTrackingService = orderTrackingService;
	}

	@PostMapping("/api/orders")
	public ResponseEntity<Order> createOrder() {
		return ResponseEntity.ok(orderTrackingService.create());
	}

	@PostMapping("/api/orders/{id}/advance")
	public ResponseEntity<Order> advanceOrder(@PathVariable String id) {
		return ResponseEntity.ok(orderTrackingService.advance(id));
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=DemoControllerTest`
Expected: PASS, 4 tests

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/controller/DemoController.java communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/DemoControllerTest.java
git commit -m "feat(communication-protocols): add REST triggers for websockets demo order tracking"
```

---

### Task 5: Raw WebSocket handler + handshake failure simulation

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/RawOrderWebSocketHandler.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/FailureSimulatingHandshakeInterceptor.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/RawWebSocketConfig.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/raw/RawOrderWebSocketHandlerTest.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/raw/FailureSimulatingHandshakeInterceptorTest.java`

**Interfaces:**
- Consumes: `OrderEventPublisher` (Task 2), `FailureSimulator.maybeThrow(String)` (Task 3)
- Produces: `RawOrderWebSocketHandler` — a Spring `@Component` implementing both `OrderEventPublisher` and (via `TextWebSocketHandler`) `WebSocketHandler`, registered at `/ws/raw/orders`. Later tasks (`OrderTrackingService`'s autowired publisher list) rely on it being an `OrderEventPublisher` bean; no other task calls it directly.

- [ ] **Step 1: Write the failing tests**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/raw/RawOrderWebSocketHandlerTest.java`:

```java
package com.testingai.websockets.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RawOrderWebSocketHandlerTest {

	private final RawOrderWebSocketHandler handler = new RawOrderWebSocketHandler(new ObjectMapper());

	@Test
	void publish_sendsEventToAllOpenSessions() throws Exception {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("session-1");
		handler.afterConnectionEstablished(session);

		handler.publish(new OrderEvent("order-1", OrderStatus.PAID, Instant.now()));

		verify(session).sendMessage(any(TextMessage.class));
	}

	@Test
	void publish_skipsSessions_afterConnectionClosed() throws Exception {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn("session-2");
		handler.afterConnectionEstablished(session);
		handler.afterConnectionClosed(session, CloseStatus.NORMAL);

		handler.publish(new OrderEvent("order-2", OrderStatus.PAID, Instant.now()));

		verify(session, never()).sendMessage(any(TextMessage.class));
	}
}
```

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/raw/FailureSimulatingHandshakeInterceptorTest.java`:

```java
package com.testingai.websockets.raw;

import com.testingai.websockets.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class FailureSimulatingHandshakeInterceptorTest {

	private final FailureSimulatingHandshakeInterceptor interceptor = new FailureSimulatingHandshakeInterceptor();
	private final ServerHttpRequest request = mock(ServerHttpRequest.class);
	private final ServerHttpResponse response = mock(ServerHttpResponse.class);
	private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

	@Test
	void beforeHandshake_returnsTrue_whenNoSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			boolean result = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

			assertThat(result).isTrue();
		}
	}

	@Test
	void beforeHandshake_returnsFalse_andSets503_whenSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			boolean result = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

			assertThat(result).isFalse();
			verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		}
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=RawOrderWebSocketHandlerTest,FailureSimulatingHandshakeInterceptorTest`
Expected: FAIL (compile errors — classes don't exist yet)

- [ ] **Step 3: Create the raw handler, interceptor, and config**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/RawOrderWebSocketHandler.java`:

```java
package com.testingai.websockets.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RawOrderWebSocketHandler extends TextWebSocketHandler implements OrderEventPublisher {

	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;

	public RawOrderWebSocketHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.put(session.getId(), session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
	}

	@Override
	public void publish(OrderEvent event) {
		sessions.values().forEach(session -> sendQuietly(session, event));
	}

	private void sendQuietly(WebSocketSession session, OrderEvent event) {
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
		} catch (IOException e) {
			sessions.remove(session.getId());
		}
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/FailureSimulatingHandshakeInterceptor.java`:

```java
package com.testingai.websockets.raw;

import com.testingai.websockets.util.FailureSimulator;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class FailureSimulatingHandshakeInterceptor implements HandshakeInterceptor {

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		try {
			FailureSimulator.maybeThrow("raw-handshake");
			return true;
		} catch (RuntimeException e) {
			response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw/RawWebSocketConfig.java`:

```java
package com.testingai.websockets.raw;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

	private final RawOrderWebSocketHandler rawOrderWebSocketHandler;

	public RawWebSocketConfig(RawOrderWebSocketHandler rawOrderWebSocketHandler) {
		this.rawOrderWebSocketHandler = rawOrderWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(rawOrderWebSocketHandler, "/ws/raw/orders")
				.addInterceptors(new FailureSimulatingHandshakeInterceptor()).setAllowedOrigins("*");
	}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=RawOrderWebSocketHandlerTest,FailureSimulatingHandshakeInterceptorTest`
Expected: PASS, 4 tests total

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/raw communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/raw
git commit -m "feat(communication-protocols): add raw WebSocket broadcast handler for websockets demo"
```

---

### Task 6: STOMP config + broadcast pattern

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/config/StompConfig.java`
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/broadcast/BroadcastPublisher.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/broadcast/BroadcastPublisherTest.java`

**Interfaces:**
- Consumes: `OrderEventPublisher` (Task 2)
- Produces: `SimpMessagingTemplate` bean (via `@EnableWebSocketMessageBroker`), two STOMP endpoints — `/ws-stomp` (SockJS) and `/ws-stomp-native` (plain) — broker destinations `/topic/**` and `/queue/**`, app-destination prefix `/app`. `BroadcastPublisher` is an `OrderEventPublisher` bean sending to `/topic/orders`.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/broadcast/BroadcastPublisherTest.java`:

```java
package com.testingai.websockets.stomp.broadcast;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BroadcastPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final BroadcastPublisher publisher = new BroadcastPublisher(messagingTemplate);

	@Test
	void publish_sendsEventToBroadcastTopic() {
		OrderEvent event = new OrderEvent("order-1", OrderStatus.PAID, Instant.now());

		publisher.publish(event);

		verify(messagingTemplate).convertAndSend(eq("/topic/orders"), eq(event));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=BroadcastPublisherTest`
Expected: FAIL (compile error — `BroadcastPublisher` doesn't exist yet)

- [ ] **Step 3: Create StompConfig and BroadcastPublisher**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/config/StompConfig.java`:

```java
package com.testingai.websockets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// /ws-stomp: SockJS-wrapped, used by the browser test client for fallback compatibility.
		registry.addEndpoint("/ws-stomp").setAllowedOriginPatterns("*").withSockJS();
		// /ws-stomp-native: plain STOMP-over-WebSocket, used by Gatling and integration tests so they can speak
		// STOMP directly without parsing the SockJS frame envelope.
		registry.addEndpoint("/ws-stomp-native").setAllowedOriginPatterns("*");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
		heartbeatScheduler.setPoolSize(1);
		heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
		heartbeatScheduler.initialize();

		registry.enableSimpleBroker("/topic", "/queue").setHeartbeatValue(new long[] { 10000, 10000 })
				.setTaskScheduler(heartbeatScheduler);
		registry.setApplicationDestinationPrefixes("/app");
	}
}
```

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/broadcast/BroadcastPublisher.java`:

```java
package com.testingai.websockets.stomp.broadcast;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class BroadcastPublisher implements OrderEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public BroadcastPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(OrderEvent event) {
		messagingTemplate.convertAndSend("/topic/orders", event);
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=BroadcastPublisherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/config communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/broadcast communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/broadcast
git commit -m "feat(communication-protocols): add STOMP config and broadcast pattern for websockets demo"
```

---

### Task 7: STOMP per-order topic pattern

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/topic/OrderTopicPublisher.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/topic/OrderTopicPublisherTest.java`

**Interfaces:**
- Consumes: `SimpMessagingTemplate` (Task 6), `OrderEventPublisher` (Task 2)
- Produces: `OrderTopicPublisher` — `OrderEventPublisher` bean sending to `/topic/orders/{orderId}`.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/topic/OrderTopicPublisherTest.java`:

```java
package com.testingai.websockets.stomp.topic;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderTopicPublisherTest {

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final OrderTopicPublisher publisher = new OrderTopicPublisher(messagingTemplate);

	@Test
	void publish_sendsEventToPerOrderTopic() {
		OrderEvent event = new OrderEvent("order-1", OrderStatus.PAID, Instant.now());

		publisher.publish(event);

		verify(messagingTemplate).convertAndSend(eq("/topic/orders/order-1"), eq(event));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderTopicPublisherTest`
Expected: FAIL (compile error — `OrderTopicPublisher` doesn't exist yet)

- [ ] **Step 3: Create OrderTopicPublisher**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/topic/OrderTopicPublisher.java`:

```java
package com.testingai.websockets.stomp.topic;

import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderTopicPublisher implements OrderEventPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public OrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(OrderEvent event) {
		messagingTemplate.convertAndSend("/topic/orders/" + event.orderId(), event);
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderTopicPublisherTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/topic communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/topic
git commit -m "feat(communication-protocols): add STOMP per-order topic pattern for websockets demo"
```

---

### Task 8: STOMP request/reply pattern

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/reqreply/OrderStatusController.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/reqreply/OrderStatusControllerTest.java`

**Interfaces:**
- Consumes: `OrderTrackingService.get(String)` (Task 2), `SimpMessagingTemplate` (Task 6), `FailureSimulator.maybeThrow(String)` (Task 3)
- Produces: `@MessageMapping("/orders/{id}/status-request")` handler that replies to the sender's private queue `/queue/orders/{id}/status` via `convertAndSendToUser`.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/reqreply/OrderStatusControllerTest.java`:

```java
package com.testingai.websockets.stomp.reqreply;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderStatus;
import com.testingai.websockets.domain.OrderTrackingService;
import com.testingai.websockets.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStatusControllerTest {

	private final OrderTrackingService orderTrackingService = mock(OrderTrackingService.class);
	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
	private final OrderStatusController controller = new OrderStatusController(orderTrackingService, messagingTemplate);

	@Test
	void statusRequest_repliesToSenderSessionQueue_withCurrentOrderState() {
		// Must mock FailureSimulator here too — otherwise this test hits the real 5% failure rate and is flaky.
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);
			Order order = new Order("order-1", OrderStatus.SHIPPED, Instant.now());
			when(orderTrackingService.get("order-1")).thenReturn(order);
			SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			headerAccessor.setSessionId("session-42");

			controller.statusRequest("order-1", headerAccessor);

			// any(Map.class) disambiguates from convertAndSendToUser's MessagePostProcessor overload
			verify(messagingTemplate).convertAndSendToUser(eq("session-42"), eq("/queue/orders/order-1/status"),
					any(), any(Map.class));
		}
	}

	@Test
	void statusRequest_propagatesException_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));
			SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			headerAccessor.setSessionId("session-42");

			assertThatThrownBy(() -> controller.statusRequest("order-1", headerAccessor))
					.isInstanceOf(RuntimeException.class).hasMessage("Simulated");
		}
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderStatusControllerTest`
Expected: FAIL (compile error — `OrderStatusController` doesn't exist yet)

- [ ] **Step 3: Create OrderStatusController**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/reqreply/OrderStatusController.java`:

```java
package com.testingai.websockets.stomp.reqreply;

import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderEvent;
import com.testingai.websockets.domain.OrderTrackingService;
import com.testingai.websockets.util.FailureSimulator;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class OrderStatusController {

	private final OrderTrackingService orderTrackingService;
	private final SimpMessagingTemplate messagingTemplate;

	public OrderStatusController(OrderTrackingService orderTrackingService, SimpMessagingTemplate messagingTemplate) {
		this.orderTrackingService = orderTrackingService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/orders/{id}/status-request")
	public void statusRequest(@DestinationVariable String id, SimpMessageHeaderAccessor headerAccessor) {
		FailureSimulator.maybeThrow("status-request");
		Order order = orderTrackingService.get(id);
		OrderEvent event = new OrderEvent(order.id(), order.status(), order.updatedAt());
		String sessionId = headerAccessor.getSessionId();
		messagingTemplate.convertAndSendToUser(sessionId, "/queue/orders/" + id + "/status", event,
				sessionHeaders(sessionId));
	}

	private MessageHeaders sessionHeaders(String sessionId) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		accessor.setSessionId(sessionId);
		accessor.setLeaveMutable(true);
		return accessor.getMessageHeaders();
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=OrderStatusControllerTest`
Expected: PASS, 2 tests

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/stomp/reqreply communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/stomp/reqreply
git commit -m "feat(communication-protocols): add STOMP request/reply pattern for websockets demo"
```

---

### Task 9: Disconnect event logging

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/disconnect/DisconnectEventListener.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/disconnect/DisconnectEventListenerTest.java`

**Interfaces:**
- Produces: `DisconnectEventListener` — `@Component` with `@EventListener` on `SessionDisconnectEvent`; package-visible `describe(SessionDisconnectEvent)` method for testability. No other task depends on this bean directly (it's a pure side-effect listener); the raw handler's own session cleanup (Task 5) already handles the raw-registry side.

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/disconnect/DisconnectEventListenerTest.java`:

```java
package com.testingai.websockets.disconnect;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DisconnectEventListenerTest {

	private final DisconnectEventListener listener = new DisconnectEventListener();

	@Test
	void describe_includesSessionIdAndCloseStatus() {
		SessionDisconnectEvent event = disconnectEvent("session-99", CloseStatus.GOING_AWAY);

		String description = listener.describe(event);

		assertThat(description).contains("session-99").contains("1001"); // CloseStatus.toString() renders the numeric code, not the constant name
	}

	@Test
	void onDisconnect_doesNotThrow() {
		SessionDisconnectEvent event = disconnectEvent("session-100", CloseStatus.NORMAL);

		listener.onDisconnect(event);
	}

	@SuppressWarnings("unchecked")
	private SessionDisconnectEvent disconnectEvent(String sessionId, CloseStatus closeStatus) {
		Message<byte[]> message = mock(Message.class);
		return new SessionDisconnectEvent(this, message, sessionId, closeStatus);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=DisconnectEventListenerTest`
Expected: FAIL (compile error — `DisconnectEventListener` doesn't exist yet)

- [ ] **Step 3: Create DisconnectEventListener**

`communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/disconnect/DisconnectEventListener.java`:

```java
package com.testingai.websockets.disconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class DisconnectEventListener {

	private static final Logger log = LoggerFactory.getLogger(DisconnectEventListener.class);

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		log.info(describe(event));
	}

	String describe(SessionDisconnectEvent event) {
		return "STOMP session " + event.getSessionId() + " disconnected, closeStatus=" + event.getCloseStatus();
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=DisconnectEventListenerTest`
Expected: PASS, 2 tests

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/java/com/testingai/websockets/disconnect communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/disconnect
git commit -m "feat(communication-protocols): log STOMP session disconnects for websockets demo"
```

---

### Task 10: End-to-end integration tests (raw + STOMP)

**Files:**
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/RawWebSocketIntegrationTest.java`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/StompIntegrationTest.java`

**Interfaces:**
- Consumes: `POST /api/orders`, `POST /api/orders/{id}/advance` (Task 4); `/ws/raw/orders` (Task 5); `/ws-stomp-native`, `/topic/orders`, `/topic/orders/{id}`, `/app/orders/{id}/status-request`, `/user/queue/orders/{id}/status` (Tasks 6–8). No new production interfaces — this task only proves the pieces work together end-to-end.

These are pure verification tests (nothing to TDD against — the production code already exists from Tasks 4–8), so there's no red/green cycle; write them and confirm they pass.

- [ ] **Step 1: Write RawWebSocketIntegrationTest**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/RawWebSocketIntegrationTest.java`:

```java
package com.testingai.websockets.controller;

import com.testingai.websockets.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RawWebSocketIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void broadcast_reachesConnectedRawClient_afterAdvance() throws Exception {
		BlockingQueue<String> received = new ArrayBlockingQueue<>(10);
		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketHandler handler = new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				received.add(message.getPayload());
			}
		};
		WebSocketSession session = client.execute(handler, "ws://localhost:" + port + "/ws/raw/orders").get(5,
				TimeUnit.SECONDS);

		try {
			Order created = restTemplate.postForObject("/api/orders", null, Order.class);
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			String message = received.poll(5, TimeUnit.SECONDS);

			assertThat(message).isNotNull().contains(created.id()).contains("PAID");
		} finally {
			session.close();
		}
	}
}
```

- [ ] **Step 2: Run it**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=RawWebSocketIntegrationTest`
Expected: PASS

- [ ] **Step 3: Write StompIntegrationTest**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/StompIntegrationTest.java`:

```java
package com.testingai.websockets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.websockets.domain.Order;
import com.testingai.websockets.domain.OrderEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	private StompSession connect() throws Exception {
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		// A bare ObjectMapper can't deserialize OrderEvent's Instant field (unlike the app's own autoconfigured
		// ObjectMapper, which registers JavaTimeModule automatically) — without this, incoming frames fail to
		// deserialize and are silently dropped by the STOMP client instead of reaching the test's frame handler.
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
		stompClient.setMessageConverter(converter);
		return stompClient.connectAsync("ws://localhost:" + port + "/ws-stomp-native", new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
	}

	private StompFrameHandler collectingHandler(BlockingQueue<OrderEvent> received) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return OrderEvent.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((OrderEvent) payload);
			}
		};
	}

	@Test
	void broadcastTopic_receivesEvent_afterAdvance() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		session.subscribe("/topic/orders", collectingHandler(received));

		try {
			Order created = restTemplate.postForObject("/api/orders", null, Order.class);
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			OrderEvent event = received.poll(5, TimeUnit.SECONDS);

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
		} finally {
			session.disconnect();
		}
	}

	@Test
	void perOrderTopic_receivesEvent_forSubscribedOrderOnly() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		Order created = restTemplate.postForObject("/api/orders", null, Order.class);
		session.subscribe("/topic/orders/" + created.id(), collectingHandler(received));

		try {
			restTemplate.postForObject("/api/orders/" + created.id() + "/advance", null, Order.class);

			OrderEvent event = received.poll(5, TimeUnit.SECONDS);

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
		} finally {
			session.disconnect();
		}
	}

	@Test
	void statusRequest_repliesOnPrivateQueue_withCurrentOrderState() throws Exception {
		BlockingQueue<OrderEvent> received = new ArrayBlockingQueue<>(10);
		StompSession session = connect();
		Order created = restTemplate.postForObject("/api/orders", null, Order.class);
		session.subscribe("/user/queue/orders/" + created.id() + "/status", collectingHandler(received));

		try {
			// FailureSimulator has a real 5% chance of dropping any single status-request; retry a few times so
			// the test isn't flaky (probability all 5 attempts fail is 0.05^5, i.e. negligible).
			OrderEvent event = null;
			for (int attempt = 0; attempt < 5 && event == null; attempt++) {
				session.send("/app/orders/" + created.id() + "/status-request", null);
				event = received.poll(2, TimeUnit.SECONDS);
			}

			assertThat(event).isNotNull();
			assertThat(event.orderId()).isEqualTo(created.id());
			assertThat(event.status().name()).isEqualTo("CREATED");
		} finally {
			session.disconnect();
		}
	}
}
```

- [ ] **Step 4: Run it**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=StompIntegrationTest`
Expected: PASS, 3 tests

- [ ] **Step 5: Run the full module test suite**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/RawWebSocketIntegrationTest.java communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/controller/StompIntegrationTest.java
git commit -m "test(communication-protocols): add end-to-end raw and STOMP integration tests for websockets demo"
```

---

### Task 11: Static browser test client

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/main/resources/static/ws-client/index.html`
- Test: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/WsClientStaticResourceTest.java`

**Interfaces:**
- Consumes: `/ws/raw/orders`, `/ws-stomp` (SockJS), `/topic/orders`, `/topic/orders/{id}`, `/app/orders/{id}/status-request`, `/user/queue/orders/{id}/status`, `POST /api/orders`, `POST /api/orders/{id}/advance` — all from prior tasks.
- Produces: a static page served at `/ws-client/index.html` (and `/ws-client/` via Spring's directory-index resolution).

- [ ] **Step 1: Write the failing test**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/WsClientStaticResourceTest.java`:

```java
package com.testingai.websockets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WsClientStaticResourceTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void wsClientPage_isServed() {
		ResponseEntity<String> response = restTemplate.getForEntity("/ws-client/index.html", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("WebSocket Demo");
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=WsClientStaticResourceTest`
Expected: FAIL (404 — the static file doesn't exist yet)

- [ ] **Step 3: Create the static test client page**

`communication-protocols/websockets/spring-demo/src/main/resources/static/ws-client/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="UTF-8">
	<title>WebSocket Demo — Order Tracking</title>
	<script src="/webjars/sockjs-client/1.5.1/sockjs.min.js"></script>
	<script src="/webjars/stomp-websocket/2.3.4/stomp.min.js"></script>
	<style>
		body { font-family: sans-serif; margin: 2rem; }
		fieldset { margin-bottom: 1rem; }
		#log { white-space: pre-wrap; background: #111; color: #0f0; padding: 1rem; height: 200px; overflow-y: scroll; }
		input { width: 4rem; }
	</style>
</head>
<body>
<h1>WebSocket Demo — Order Tracking</h1>

<fieldset>
	<legend>Order</legend>
	<button id="create-order">Create order</button>
	<span id="current-order">no order yet</span>
	<button id="advance-order">Advance order</button>
</fieldset>

<fieldset>
	<legend>Raw WebSocket (/ws/raw/orders)</legend>
	<button id="raw-connect">Connect</button>
	<button id="raw-disconnect">Disconnect</button>
</fieldset>

<fieldset>
	<legend>STOMP (/ws-stomp)</legend>
	<button id="stomp-connect">Connect</button>
	<button id="stomp-disconnect">Disconnect</button>
	<br>
	Order id for per-order topic / request-reply: <input id="topic-order-id">
	<button id="stomp-subscribe-topic">Subscribe to order topic</button>
	<button id="stomp-request-status">Request status</button>
</fieldset>

<div id="log"></div>

<script>
	const logEl = document.getElementById('log');
	function log(line) {
		logEl.textContent += new Date().toISOString() + ' ' + line + '\n';
		logEl.scrollTop = logEl.scrollHeight;
	}

	let currentOrderId = null;
	document.getElementById('create-order').addEventListener('click', () => {
		fetch('/api/orders', { method: 'POST' }).then(r => r.json()).then(order => {
			currentOrderId = order.id;
			document.getElementById('current-order').textContent = order.id + ' (' + order.status + ')';
			document.getElementById('topic-order-id').value = order.id;
			log('Created order ' + order.id);
		});
	});
	document.getElementById('advance-order').addEventListener('click', () => {
		if (!currentOrderId) { log('No order created yet'); return; }
		fetch('/api/orders/' + currentOrderId + '/advance', { method: 'POST' }).then(r => {
			if (!r.ok) { log('Advance failed: HTTP ' + r.status); return; }
			return r.json().then(order => {
				document.getElementById('current-order').textContent = order.id + ' (' + order.status + ')';
				log('Advanced order ' + order.id + ' to ' + order.status);
			});
		});
	});

	let rawSocket = null;
	document.getElementById('raw-connect').addEventListener('click', () => {
		rawSocket = new WebSocket('ws://' + location.host + '/ws/raw/orders');
		rawSocket.onopen = () => log('[raw] connected');
		rawSocket.onmessage = e => log('[raw] received: ' + e.data);
		rawSocket.onclose = e => log('[raw] closed: code=' + e.code);
		rawSocket.onerror = () => log('[raw] error — connection may have been rejected by the simulated 5% failure rate');
	});
	document.getElementById('raw-disconnect').addEventListener('click', () => {
		if (rawSocket) rawSocket.close();
	});

	let stompClient = null;
	document.getElementById('stomp-connect').addEventListener('click', () => {
		const socket = new SockJS('/ws-stomp');
		stompClient = Stomp.over(socket);
		stompClient.connect({}, () => {
			log('[stomp] connected');
			stompClient.subscribe('/topic/orders', message => {
				log('[stomp] broadcast: ' + message.body);
			});
		}, error => {
			log('[stomp] error: ' + error);
		});
	});
	document.getElementById('stomp-disconnect').addEventListener('click', () => {
		if (stompClient) stompClient.disconnect(() => log('[stomp] disconnected'));
	});
	document.getElementById('stomp-subscribe-topic').addEventListener('click', () => {
		const orderId = document.getElementById('topic-order-id').value;
		if (!stompClient || !orderId) { log('Connect STOMP and enter an order id first'); return; }
		stompClient.subscribe('/topic/orders/' + orderId, message => {
			log('[stomp] per-order topic (' + orderId + '): ' + message.body);
		});
		log('[stomp] subscribed to /topic/orders/' + orderId);
	});
	document.getElementById('stomp-request-status').addEventListener('click', () => {
		const orderId = document.getElementById('topic-order-id').value;
		if (!stompClient || !orderId) { log('Connect STOMP and enter an order id first'); return; }
		stompClient.subscribe('/user/queue/orders/' + orderId + '/status', message => {
			log('[stomp] status reply: ' + message.body);
		});
		stompClient.send('/app/orders/' + orderId + '/status-request', {}, '');
		log('[stomp] sent status request for ' + orderId + ' (may be dropped by the simulated 5% failure rate)');
	});
</script>
</body>
</html>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test -Dtest=WsClientStaticResourceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/main/resources/static communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/WsClientStaticResourceTest.java
git commit -m "feat(communication-protocols): add static browser test client for websockets demo"
```

---

### Task 12: Gatling load test

**Files:**
- Create: `communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: `POST /api/orders`, `POST /api/orders/{id}/advance` (Task 4), `/ws/raw/orders` (Task 5), `/ws-stomp-native` + `/topic/orders` (Task 6). No new production interfaces.

This file is excluded from `mvn test` by the inherited surefire `**/performance/**` exclude — there is nothing to TDD here, just author the load script and confirm it compiles.

- [ ] **Step 1: Create the Gatling simulation**

`communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/performance/DemoSimulation.java`:

Note on the Gatling API: WebSocket support lives in the `io.gatling.javaapi.http` package (there is no separate `io.gatling.javaapi.ws` artifact) and runs over the same `HttpProtocolBuilder` — `ws://` URLs are derived automatically from the `http.baseUrl(...)` scheme. `.await(Duration).on(...)` is only available chained directly onto a `.connect(...)` or `.sendText(...)` call (verified against the installed `gatling-http-java-3.13.1.jar`), not as a standalone action. To await the raw broadcast and the STOMP topic message — both pushed asynchronously as a side effect of the `Advance Order` HTTP call, not as a direct reply to something just sent — this simulation sends a harmless probe frame (a no-op `"ping"` on the raw socket, a STOMP heartbeat `"\n"` on the STOMP socket) purely to give `.await()` an action to attach to; the server ignores both.

```java
package com.testingai.websockets.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.regex;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.gatling.javaapi.http.HttpDsl.ws;

public class DemoSimulation extends Simulation {

	private static final String STOMP_CONNECT_FRAME = "CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\u0000";
	private static final String STOMP_SUBSCRIBE_FRAME = "SUBSCRIBE\nid:sub-0\ndestination:/topic/orders\n\n\u0000";
	private static final String STOMP_HEARTBEAT_FRAME = "\n";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8098");

	private final ScenarioBuilder demoScenario = scenario("WebSocket Demo")
			.exec(http("Create Order").post("/api/orders").check(status().is(200))
					.check(jsonPath("$.id").saveAs("orderId")))
			.exec(ws("Connect Raw").wsName("raw").connect("/ws/raw/orders"))
			.exec(ws("Connect STOMP").wsName("stomp").connect("/ws-stomp-native"))
			.exec(ws("STOMP Connect Frame").wsName("stomp").sendText(STOMP_CONNECT_FRAME).await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("stomp-connected-check").check(regex("CONNECTED"))))
			.exec(ws("STOMP Subscribe").wsName("stomp").sendText(STOMP_SUBSCRIBE_FRAME))
			.pause(Duration.ofMillis(200))
			.exec(http("Advance Order").post("/api/orders/#{orderId}/advance").check(status().is(200)))
			.exec(ws("Await Raw Broadcast").wsName("raw").sendText("ping").await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("raw-broadcast-check").check(regex("#{orderId}"))))
			.exec(ws("Await STOMP Message").wsName("stomp").sendText(STOMP_HEARTBEAT_FRAME).await(Duration.ofSeconds(5))
					.on(ws.checkTextMessage("stomp-message-check").check(regex("MESSAGE"))))
			.exec(ws("Close Raw").wsName("raw").close())
			.exec(ws("Close STOMP").wsName("stomp").close());

	{
		// 2 users, ramped a few seconds apart, matching the pacing convention from graphql/spring-demo's DemoSimulation.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
```

- [ ] **Step 2: Verify the Gatling simulation compiles**

Run: `cd communication-protocols && mvn -pl websockets/spring-demo test-compile`
Expected: BUILD SUCCESS (this only compiles the simulation class; it does not execute it, since Gatling is excluded from `mvn test`)

- [ ] **Step 3: Commit**

```bash
git add communication-protocols/websockets/spring-demo/src/test/java/com/testingai/websockets/performance
git commit -m "test(communication-protocols): add Gatling load test for websockets demo"
```

---

### Task 13: Documentation

**Files:**
- Create: `communication-protocols/websockets/README.md`
- Modify: `communication-protocols/README.md`
- Modify: `CLAUDE.md` (repo root)

**Interfaces:** None — documentation only.

- [ ] **Step 1: Write the module README**

`communication-protocols/websockets/README.md`:

```markdown
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
```

- [ ] **Step 2: Update the root communication-protocols README**

Edit `communication-protocols/README.md`, replace the table and closing line:

```markdown
| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |
| [GraphQL](graphql/) | Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns | Client-driven field selection over one endpoint; aggregating/relational data from a single request |
| [Webhooks](webhooks/) | Two independent Spring Boot apps — producer (subscriptions, HMAC-signed dispatch, retry/backoff, dead-lettering) + consumer (verification, idempotency/dedup) | Async server-to-server push where the receiver can't poll; event notifications between independently-owned systems |
| [WebSocket](websockets/) | Single Spring Boot app — raw WebSocket handler + STOMP broadcast/per-order-topic/request-reply patterns, plus a static browser test client | Persistent full-duplex connections; live updates pushed to many subscribed clients without polling |

More protocol demos may be added here over time.
```

- [ ] **Step 3: Update root CLAUDE.md**

Edit `/Users/admin/IdeaProjects/private/techmix-copy/CLAUDE.md`. Insert this new command section right after the "Webhooks communication protocol demo" section and before "Camunda workflow engine demo":

```markdown
### WebSocket communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                      # build (part of the reactor build)
mvn test -pl websockets/spring-demo                     # unit tests (Gatling excluded automatically)
mvn test -pl websockets/spring-demo -Dtest=ClassName    # single test class
mvn -pl websockets/spring-demo spring-boot:run          # run the app (test client at :8098/ws-client/index.html)
mvn gatling:test -pl websockets/spring-demo             # Gatling load test — requires the app running first
```
```

Then, in the "Repository layout" table, add a row after the Webhooks row:

```markdown
| `communication-protocols/websockets/spring-demo/` | WebSocket demo — raw `WebSocketHandler` broadcast, STOMP broadcast/per-order-topic/request-reply patterns, disconnect handling, and a static browser test client over an order-tracking domain — no external infrastructure required |
```

- [ ] **Step 4: Commit**

```bash
git add communication-protocols/websockets/README.md communication-protocols/README.md CLAUDE.md
git commit -m "docs(communication-protocols): document the websockets demo"
```

---

### Task 14: Full reactor verification

**Files:** None — verification only.

**Interfaces:** None.

- [ ] **Step 1: Build the whole communication-protocols reactor**

Run: `cd communication-protocols && mvn clean package`
Expected: BUILD SUCCESS across all modules (grpc, graphql, webhooks, websockets)

- [ ] **Step 2: Run the whole reactor's test suite**

Run: `cd communication-protocols && mvn test`
Expected: BUILD SUCCESS, all tests pass across all modules

- [ ] **Step 3: If anything fails, fix it**

If a failure is isolated to the new `websockets/spring-demo` module, fix it there and re-run Step 2. If a failure appears in an unrelated existing module, stop and report it — it's not something this plan should touch.

- [ ] **Step 4: Confirm git status is clean**

Run: `git status`
Expected: nothing to commit (everything was committed at the end of each task)
