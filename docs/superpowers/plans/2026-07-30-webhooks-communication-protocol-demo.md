# Webhooks Communication Protocol Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `webhooks/` module under `communication-protocols/` with two independent Spring Boot apps — `producer-demo` (subscriptions, HMAC-signed dispatch, retry/backoff, dead-lettering) and `consumer-demo` (signature verification, idempotency/dedup, on-demand failure simulation) — demonstrating the five core webhook patterns end-to-end.

**Architecture:** Producer owns an in-memory subscription registry and a `WebhookDispatcher` that signs and POSTs events via Spring's `RestClient`, retrying failed deliveries via `TaskScheduler` with exponential backoff before dead-lettering. Consumer exposes a single receiving endpoint that verifies the HMAC signature, dedups by delivery id, and exposes an admin endpoint to arm deterministic failures so retry/backoff/dead-letter behavior is observable on demand.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring's `RestClient` + `TaskScheduler` (`SimpleAsyncTaskScheduler` with virtual threads), Jackson, JUnit 5, Mockito, AssertJ, OkHttp `MockWebServer`, Awaitility, Gatling, JMeter.

## Global Constraints

- Java 21 / Spring Boot 3.4.4, inherited from `communication-protocols/pom.xml` — do not override.
- No Docker / external infrastructure — both apps run standalone via `mvn spring-boot:run`.
- All state is in-memory (`ConcurrentHashMap`/lists) — no persistence, no distributed store.
- Ports: producer-demo `8096`, consumer-demo `8097` (next free slots after `8095`).
- HMAC-SHA256 only, one secret per subscription — no algorithm negotiation, no secret rotation.
- Retry policy: 5 attempts, backoff `1s, 2s, 4s, 8s, 16s`, then dead-letter — fixed, not configurable via `application.yml`, but internally parameterized (`RetryBackoffSchedule`) so tests can use a fast schedule instead of waiting real seconds.
- `communication-protocols/**.java` is already covered by `.githooks/pre-commit`'s Spotless auto-format — no build changes needed there.
- Package roots: `com.testingai.webhooks.producer` and `com.testingai.webhooks.consumer`.
- Every task's Java files must compile under the existing Eclipse formatter config; run `mvn spotless:apply` from `communication-protocols/` if unsure, but do not hand-format — the pre-commit hook does this automatically on commit.

---

### Task 1: producer-demo scaffold + HmacSigner

**Files:**
- Create: `communication-protocols/webhooks/producer-demo/pom.xml`
- Create: `communication-protocols/webhooks/producer-demo/src/main/resources/application.yml`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/WebhooksProducerDemoApplication.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/security/HmacSigner.java`
- Modify: `communication-protocols/pom.xml` (add `webhooks/producer-demo` to `<modules>`)
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/WebhooksProducerDemoApplicationTest.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/security/HmacSignerTest.java`

**Interfaces:**
- Produces: `HmacSigner.sign(String secret, String payload) -> String` (lowercase hex HMAC-SHA256 digest, no `sha256=` prefix) — used by `WebhookDispatcher` in Task 3.

- [ ] **Step 1: Add the module to the parent reactor**

Edit `communication-protocols/pom.xml`:

```xml
    <modules>
        <module>grpc/server-demo</module>
        <module>grpc/client-demo</module>
        <module>graphql/spring-demo</module>
        <module>webhooks/producer-demo</module>
        <module>webhooks/consumer-demo</module>
    </modules>
```

(Both modules are listed now even though `consumer-demo` doesn't exist until Task 6 — Maven only complains about a missing module directory when you actually try to build it, and listing both up front avoids a second edit later.)

- [ ] **Step 2: Create the producer-demo pom.xml**

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

    <artifactId>webhooks-producer-demo</artifactId>
    <name>Webhooks Producer Demo</name>
    <description>Subscription registry and HMAC-signed webhook dispatcher with retry/backoff and dead-lettering</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>4.2.2</version>
            <scope>test</scope>
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
                    <mainClass>com.testingai.webhooks.producer.WebhooksProducerDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.webhooks.producer.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>jmeter-load-test</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.lazerycode.jmeter</groupId>
                        <artifactId>jmeter-maven-plugin</artifactId>
                        <version>${jmeter-maven-plugin.version}</version>
                        <executions>
                            <execution>
                                <id>configuration</id>
                                <goals>
                                    <goal>configure</goal>
                                </goals>
                            </execution>
                            <execution>
                                <id>jmeter-tests</id>
                                <goals>
                                    <goal>jmeter</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 3: Create application.yml**

`communication-protocols/webhooks/producer-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8096
```

- [ ] **Step 4: Create the main application class**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/WebhooksProducerDemoApplication.java`:

```java
package com.testingai.webhooks.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebhooksProducerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebhooksProducerDemoApplication.class, args);
	}
}
```

- [ ] **Step 5: Write the failing test for HmacSigner**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/security/HmacSignerTest.java`:

```java
package com.testingai.webhooks.producer.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignerTest {

	private final HmacSigner hmacSigner = new HmacSigner();

	@Test
	void sign_producesStableHexDigest_forKnownSecretAndPayload() {
		String signature = hmacSigner.sign("test-secret", "{\"hello\":\"world\"}");

		assertThat(signature).isEqualTo("84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");
	}

	@Test
	void sign_producesDifferentDigests_forDifferentSecrets() {
		String signatureA = hmacSigner.sign("secret-a", "same payload");
		String signatureB = hmacSigner.sign("secret-b", "same payload");

		assertThat(signatureA).isNotEqualTo(signatureB);
	}

	@Test
	void sign_producesDifferentDigests_forDifferentPayloads() {
		String signatureA = hmacSigner.sign("same-secret", "payload a");
		String signatureB = hmacSigner.sign("same-secret", "payload b");

		assertThat(signatureA).isNotEqualTo(signatureB);
	}
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=HmacSignerTest`
Expected: FAIL — compilation error, `HmacSigner` does not exist.

- [ ] **Step 7: Implement HmacSigner**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/security/HmacSigner.java`:

```java
package com.testingai.webhooks.producer.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HmacSigner {

	private static final String ALGORITHM = "HmacSHA256";

	public String sign(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Unable to compute HMAC signature", e);
		}
	}
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=HmacSignerTest`
Expected: PASS (3 tests)

- [ ] **Step 9: Write and run the application context test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/WebhooksProducerDemoApplicationTest.java`:

```java
package com.testingai.webhooks.producer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebhooksProducerDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=WebhooksProducerDemoApplicationTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add communication-protocols/pom.xml communication-protocols/webhooks/producer-demo
git commit -m "feat(communication-protocols): scaffold webhooks-producer-demo with HmacSigner"
```

---

### Task 2: producer-demo subscription registry

**Files:**
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/Subscription.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/SubscriptionService.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/SubscriptionController.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/subscription/SubscriptionServiceTest.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/subscription/SubscriptionControllerTest.java`

**Interfaces:**
- Produces: `record Subscription(String id, String callbackUrl, String secret, Set<String> eventTypes)`
- Produces: `SubscriptionService.register(String callbackUrl, String secret, Set<String> eventTypes) -> Subscription`
- Produces: `SubscriptionService.findAll() -> List<Subscription>`
- Produces: `SubscriptionService.findByEventType(String eventType) -> List<Subscription>` — consumed by `OrderEventController` in Task 4.
- Produces: `SubscriptionService.remove(String id) -> boolean`

- [ ] **Step 1: Write the failing SubscriptionService test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/subscription/SubscriptionServiceTest.java`:

```java
package com.testingai.webhooks.producer.subscription;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionServiceTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();

	@Test
	void register_assignsIdAndStoresSubscription() {
		Subscription subscription = subscriptionService.register("http://localhost:8097/webhooks/orders", "secret",
				Set.of("order.created"));

		assertThat(subscription.id()).isNotBlank();
		assertThat(subscriptionService.findAll()).containsExactly(subscription);
	}

	@Test
	void register_defaultsToEmptyEventTypes_whenNullPassed() {
		Subscription subscription = subscriptionService.register("http://localhost:8097/webhooks/orders", "secret",
				null);

		assertThat(subscription.eventTypes()).isEmpty();
	}

	@Test
	void findByEventType_returnsOnlyMatchingSubscriptions() {
		subscriptionService.register("http://a", "secret", Set.of("order.created"));
		Subscription paidSubscription = subscriptionService.register("http://b", "secret", Set.of("order.paid"));

		assertThat(subscriptionService.findByEventType("order.paid")).containsExactly(paidSubscription);
	}

	@Test
	void remove_deletesSubscription_andReturnsTrue() {
		Subscription subscription = subscriptionService.register("http://a", "secret", Set.of("order.created"));

		boolean removed = subscriptionService.remove(subscription.id());

		assertThat(removed).isTrue();
		assertThat(subscriptionService.findAll()).isEmpty();
	}

	@Test
	void remove_returnsFalse_whenIdUnknown() {
		assertThat(subscriptionService.remove("unknown-id")).isFalse();
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=SubscriptionServiceTest`
Expected: FAIL — compilation error, `Subscription`/`SubscriptionService` do not exist.

- [ ] **Step 3: Implement Subscription and SubscriptionService**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/Subscription.java`:

```java
package com.testingai.webhooks.producer.subscription;

import java.util.Set;

public record Subscription(String id, String callbackUrl, String secret, Set<String> eventTypes) {
}
```

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/SubscriptionService.java`:

```java
package com.testingai.webhooks.producer.subscription;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionService {

	private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

	public Subscription register(String callbackUrl, String secret, Set<String> eventTypes) {
		String id = UUID.randomUUID().toString();
		Subscription subscription = new Subscription(id, callbackUrl, secret,
				Objects.requireNonNullElse(eventTypes, Set.of()));
		subscriptions.put(id, subscription);
		return subscription;
	}

	public List<Subscription> findAll() {
		return List.copyOf(subscriptions.values());
	}

	public List<Subscription> findByEventType(String eventType) {
		return subscriptions.values().stream().filter(subscription -> subscription.eventTypes().contains(eventType))
				.toList();
	}

	public boolean remove(String id) {
		return subscriptions.remove(id) != null;
	}
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=SubscriptionServiceTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing SubscriptionController test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/subscription/SubscriptionControllerTest.java`:

```java
package com.testingai.webhooks.producer.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscriptionService))
			.build();

	@BeforeEach
	void resetState() {
		subscriptionService.findAll().forEach(subscription -> subscriptionService.remove(subscription.id()));
	}

	@Test
	void register_returns201_andEchoesCallbackUrlAndEventTypes() throws Exception {
		mockMvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON).content("""
				{"callbackUrl":"http://localhost:8097/webhooks/orders","secret":"s3cret","eventTypes":["order.created"]}
				""")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.callbackUrl").value("http://localhost:8097/webhooks/orders"))
				.andExpect(jsonPath("$.eventTypes", hasSize(1)));
	}

	@Test
	void list_returnsAllRegisteredSubscriptions() throws Exception {
		subscriptionService.register("http://a", "secret", java.util.Set.of("order.created"));
		subscriptionService.register("http://b", "secret", java.util.Set.of("order.paid"));

		mockMvc.perform(get("/subscriptions")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void delete_returns204_whenSubscriptionExists() throws Exception {
		Subscription subscription = subscriptionService.register("http://a", "secret",
				java.util.Set.of("order.created"));

		mockMvc.perform(delete("/subscriptions/{id}", subscription.id())).andExpect(status().isNoContent());
	}

	@Test
	void delete_returns404_whenSubscriptionUnknown() throws Exception {
		mockMvc.perform(delete("/subscriptions/{id}", "unknown-id")).andExpect(status().isNotFound());
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=SubscriptionControllerTest`
Expected: FAIL — compilation error, `SubscriptionController` does not exist.

- [ ] **Step 7: Implement SubscriptionController**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription/SubscriptionController.java`:

```java
package com.testingai.webhooks.producer.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

	public record SubscriptionRequest(String callbackUrl, String secret, Set<String> eventTypes) {
	}

	public record SubscriptionResponse(String id, String callbackUrl, Set<String> eventTypes) {
	}

	private final SubscriptionService subscriptionService;

	public SubscriptionController(SubscriptionService subscriptionService) {
		this.subscriptionService = subscriptionService;
	}

	@PostMapping
	public ResponseEntity<SubscriptionResponse> register(@RequestBody SubscriptionRequest request) {
		Subscription subscription = subscriptionService.register(request.callbackUrl(), request.secret(),
				request.eventTypes());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(subscription));
	}

	@GetMapping
	public List<SubscriptionResponse> list() {
		return subscriptionService.findAll().stream().map(this::toResponse).toList();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		return subscriptionService.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private SubscriptionResponse toResponse(Subscription subscription) {
		return new SubscriptionResponse(subscription.id(), subscription.callbackUrl(), subscription.eventTypes());
	}
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=SubscriptionControllerTest`
Expected: PASS (4 tests)

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/subscription communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/subscription
git commit -m "feat(communication-protocols): add subscription registry to webhooks-producer-demo"
```

---

### Task 3: producer-demo WebhookDispatcher (signing, retry/backoff, dead-lettering)

**Files:**
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event/OrderEvent.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryStatus.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryAttempt.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/RetryBackoffSchedule.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/WebhookDispatcher.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/config/DispatchConfig.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery/WebhookDispatcherTest.java`

**Interfaces:**
- Consumes: `HmacSigner.sign(String, String) -> String` (Task 1); `Subscription` (Task 2).
- Produces: `record OrderEvent(String eventType, String orderId, Instant occurredAt, Map<String, Object> data)` — consumed by `OrderEventController` (Task 4) and mirrored by consumer-demo's `IncomingOrderEvent` (Task 8).
- Produces: `enum DeliveryStatus { PENDING, RETRYING, SUCCEEDED, DEAD_LETTERED }`
- Produces: `WebhookDispatcher.dispatch(Subscription subscription, OrderEvent event) -> String deliveryId` — consumed by `OrderEventController` (Task 4).
- Produces: `WebhookDispatcher.deliveries() -> Collection<DeliveryAttempt>`, `WebhookDispatcher.deadLetters() -> Collection<DeliveryAttempt>` — consumed by `DeliveryController` (Task 4).
- Produces on `DeliveryAttempt`: `deliveryId()`, `subscriptionId()`, `eventType()`, `status()`, `attemptCount()`, `nextRetryAt()` (all read accessors) — consumed by `DeliveryController` (Task 4).

- [ ] **Step 1: Create OrderEvent, DeliveryStatus, RetryBackoffSchedule (no test needed — plain data holders exercised by the dispatcher test below)**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event/OrderEvent.java`:

```java
package com.testingai.webhooks.producer.event;

import java.time.Instant;
import java.util.Map;

public record OrderEvent(String eventType, String orderId, Instant occurredAt, Map<String, Object> data) {
}
```

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryStatus.java`:

```java
package com.testingai.webhooks.producer.delivery;

public enum DeliveryStatus {
	PENDING, RETRYING, SUCCEEDED, DEAD_LETTERED
}
```

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/RetryBackoffSchedule.java`:

```java
package com.testingai.webhooks.producer.delivery;

import java.time.Duration;
import java.util.List;

/**
 * The Nth entry is the delay before retry attempt N+1. {@code delays.size()} is the maximum number of attempts
 * before a delivery is dead-lettered. Externalized (rather than a hardcoded constant in {@link WebhookDispatcher})
 * so tests can use a millisecond-scale schedule instead of waiting through the real 1s/2s/4s/8s/16s production
 * backoff.
 */
public record RetryBackoffSchedule(List<Duration> delays) {

	public int maxAttempts() {
		return delays.size();
	}

	public Duration delayForAttempt(int attemptNumber) {
		return delays.get(attemptNumber - 1);
	}
}
```

- [ ] **Step 2: Create DeliveryAttempt (mutable state, no test needed — exercised by the dispatcher test below)**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryAttempt.java`:

```java
package com.testingai.webhooks.producer.delivery;

import java.time.Instant;

public class DeliveryAttempt {

	private final String deliveryId;
	private final String subscriptionId;
	private final String eventType;
	private final String body;
	private final String callbackUrl;
	private final String secret;

	private volatile DeliveryStatus status = DeliveryStatus.PENDING;
	private volatile int attemptCount = 0;
	private volatile Instant nextRetryAt;

	public DeliveryAttempt(String deliveryId, String subscriptionId, String eventType, String body,
			String callbackUrl, String secret) {
		this.deliveryId = deliveryId;
		this.subscriptionId = subscriptionId;
		this.eventType = eventType;
		this.body = body;
		this.callbackUrl = callbackUrl;
		this.secret = secret;
	}

	public void incrementAttemptCount() {
		attemptCount++;
	}

	public void markSucceeded() {
		status = DeliveryStatus.SUCCEEDED;
		nextRetryAt = null;
	}

	public void markRetrying(Instant nextRetryAt) {
		status = DeliveryStatus.RETRYING;
		this.nextRetryAt = nextRetryAt;
	}

	public void markDeadLettered() {
		status = DeliveryStatus.DEAD_LETTERED;
		nextRetryAt = null;
	}

	public String deliveryId() {
		return deliveryId;
	}

	public String subscriptionId() {
		return subscriptionId;
	}

	public String eventType() {
		return eventType;
	}

	public String body() {
		return body;
	}

	public String callbackUrl() {
		return callbackUrl;
	}

	public String secret() {
		return secret;
	}

	public DeliveryStatus status() {
		return status;
	}

	public int attemptCount() {
		return attemptCount;
	}

	public Instant nextRetryAt() {
		return nextRetryAt;
	}
}
```

- [ ] **Step 3: Write the failing WebhookDispatcher test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery/WebhookDispatcherTest.java`:

```java
package com.testingai.webhooks.producer.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.webhooks.producer.event.OrderEvent;
import com.testingai.webhooks.producer.security.HmacSigner;
import com.testingai.webhooks.producer.subscription.Subscription;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebhookDispatcherTest {

	private MockWebServer server;
	private HmacSigner hmacSigner;
	private WebhookDispatcher dispatcher;

	@BeforeEach
	void startServer() throws IOException {
		server = new MockWebServer();
		server.start();
		hmacSigner = new HmacSigner();
	}

	@AfterEach
	void stopServer() throws IOException {
		server.shutdown();
	}

	private WebhookDispatcher dispatcherWithSchedule(List<Duration> backoff) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(2));
		RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
		SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		return new WebhookDispatcher(restClient, taskScheduler, hmacSigner, objectMapper,
				new RetryBackoffSchedule(backoff));
	}

	private Subscription subscriptionFor(String secret) {
		return new Subscription("sub-1", server.url("/webhooks/orders").toString(), secret, Set.of("order.created"));
	}

	@Test
	void dispatch_succeedsOnFirstAttempt_andSignsPayloadWithSubscriptionSecret() throws InterruptedException {
		server.enqueue(new MockResponse().setResponseCode(200));
		dispatcher = dispatcherWithSchedule(List.of(Duration.ofMillis(50)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS)
				.until(() -> dispatcher.deliveries().stream()
						.anyMatch(attempt -> attempt.deliveryId().equals(deliveryId)
								&& attempt.status() == DeliveryStatus.SUCCEEDED));

		RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
		assertThat(recorded).isNotNull();
		assertThat(recorded.getPath()).isEqualTo("/webhooks/orders");
		assertThat(recorded.getHeader("X-Webhook-Id")).isEqualTo(deliveryId);
		assertThat(recorded.getHeader("X-Webhook-Event")).isEqualTo("order.created");
		String expectedSignature = "sha256=" + hmacSigner.sign("test-secret", recorded.getBody().readUtf8());
		assertThat(recorded.getHeader("X-Webhook-Signature")).isEqualTo(expectedSignature);
	}

	@Test
	void dispatch_retriesWithBackoff_thenSucceeds() {
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(200));
		dispatcher = dispatcherWithSchedule(List.of(Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofMillis(50)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-2", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS)
				.until(() -> dispatcher.deliveries().stream()
						.anyMatch(attempt -> attempt.deliveryId().equals(deliveryId)
								&& attempt.status() == DeliveryStatus.SUCCEEDED));

		DeliveryAttempt attempt = dispatcher.deliveries().stream()
				.filter(candidate -> candidate.deliveryId().equals(deliveryId)).findFirst().orElseThrow();
		assertThat(attempt.attemptCount()).isEqualTo(3);
		assertThat(server.getRequestCount()).isEqualTo(3);
	}

	@Test
	void dispatch_deadLetters_afterExhaustingAllAttempts() {
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setResponseCode(500));
		dispatcher = dispatcherWithSchedule(List.of(Duration.ofMillis(30), Duration.ofMillis(30)));
		Subscription subscription = subscriptionFor("test-secret");
		OrderEvent event = new OrderEvent("order.created", "order-3", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of());

		String deliveryId = dispatcher.dispatch(subscription, event);

		await().atMost(2, TimeUnit.SECONDS)
				.until(() -> dispatcher.deliveries().stream()
						.anyMatch(attempt -> attempt.deliveryId().equals(deliveryId)
								&& attempt.status() == DeliveryStatus.DEAD_LETTERED));

		assertThat(dispatcher.deadLetters()).extracting(DeliveryAttempt::deliveryId).containsExactly(deliveryId);
		assertThat(server.getRequestCount()).isEqualTo(2);
	}
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=WebhookDispatcherTest`
Expected: FAIL — compilation error, `WebhookDispatcher` does not exist.

- [ ] **Step 5: Implement WebhookDispatcher**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/WebhookDispatcher.java`:

```java
package com.testingai.webhooks.producer.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.webhooks.producer.event.OrderEvent;
import com.testingai.webhooks.producer.security.HmacSigner;
import com.testingai.webhooks.producer.subscription.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebhookDispatcher {

	private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

	private final RestClient restClient;
	private final TaskScheduler taskScheduler;
	private final HmacSigner hmacSigner;
	private final ObjectMapper objectMapper;
	private final RetryBackoffSchedule retryBackoffSchedule;
	private final Map<String, DeliveryAttempt> deliveries = new ConcurrentHashMap<>();

	public WebhookDispatcher(RestClient restClient, TaskScheduler taskScheduler, HmacSigner hmacSigner,
			ObjectMapper objectMapper, RetryBackoffSchedule retryBackoffSchedule) {
		this.restClient = restClient;
		this.taskScheduler = taskScheduler;
		this.hmacSigner = hmacSigner;
		this.objectMapper = objectMapper;
		this.retryBackoffSchedule = retryBackoffSchedule;
	}

	public String dispatch(Subscription subscription, OrderEvent event) {
		String deliveryId = UUID.randomUUID().toString();
		String body = writeJson(event);
		DeliveryAttempt attempt = new DeliveryAttempt(deliveryId, subscription.id(), event.eventType(), body,
				subscription.callbackUrl(), subscription.secret());
		deliveries.put(deliveryId, attempt);
		attemptDelivery(attempt);
		return deliveryId;
	}

	public Collection<DeliveryAttempt> deliveries() {
		return deliveries.values();
	}

	public Collection<DeliveryAttempt> deadLetters() {
		return deliveries.values().stream().filter(attempt -> attempt.status() == DeliveryStatus.DEAD_LETTERED)
				.toList();
	}

	private void attemptDelivery(DeliveryAttempt attempt) {
		attempt.incrementAttemptCount();
		try {
			String signature = "sha256=" + hmacSigner.sign(attempt.secret(), attempt.body());
			restClient.post().uri(attempt.callbackUrl()).header("X-Webhook-Id", attempt.deliveryId())
					.header("X-Webhook-Event", attempt.eventType()).header("X-Webhook-Signature", signature)
					.contentType(MediaType.APPLICATION_JSON).body(attempt.body()).retrieve().toBodilessEntity();
			attempt.markSucceeded();
			log.info("delivery {} succeeded on attempt {}", attempt.deliveryId(), attempt.attemptCount());
		} catch (RestClientException e) {
			handleFailure(attempt);
		}
	}

	private void handleFailure(DeliveryAttempt attempt) {
		if (attempt.attemptCount() >= retryBackoffSchedule.maxAttempts()) {
			attempt.markDeadLettered();
			log.warn("delivery {} dead-lettered after {} attempts", attempt.deliveryId(), attempt.attemptCount());
			return;
		}
		Instant nextRetryAt = Instant.now().plus(retryBackoffSchedule.delayForAttempt(attempt.attemptCount()));
		attempt.markRetrying(nextRetryAt);
		log.info("delivery {} failed on attempt {}, retrying at {}", attempt.deliveryId(), attempt.attemptCount(),
				nextRetryAt);
		taskScheduler.schedule(() -> attemptDelivery(attempt), nextRetryAt);
	}

	private String writeJson(OrderEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unable to serialize order event", e);
		}
	}
}
```

- [ ] **Step 6: Create DispatchConfig (the production RestClient/TaskScheduler/RetryBackoffSchedule beans)**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/config/DispatchConfig.java`:

```java
package com.testingai.webhooks.producer.config;

import com.testingai.webhooks.producer.delivery.RetryBackoffSchedule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class DispatchConfig {

	@Bean
	public RestClient webhookRestClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(2));
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	@Bean
	public TaskScheduler webhookTaskScheduler() {
		SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
		taskScheduler.setVirtualThreads(true);
		taskScheduler.setThreadNamePrefix("webhook-retry-");
		return taskScheduler;
	}

	@Bean
	public RetryBackoffSchedule retryBackoffSchedule() {
		return new RetryBackoffSchedule(
				List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8),
						Duration.ofSeconds(16)));
	}
}
```

- [ ] **Step 7: Run the dispatcher test to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=WebhookDispatcherTest`
Expected: PASS (3 tests). The retry and dead-letter tests take under 200ms each thanks to the millisecond-scale `RetryBackoffSchedule` passed in by the test — they do not wait through the real 1s–16s production backoff.

- [ ] **Step 8: Run the full producer-demo test suite so far**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test`
Expected: PASS (all tests from Tasks 1–3)

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/config communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery
git commit -m "feat(communication-protocols): add WebhookDispatcher with HMAC signing, retry/backoff, and dead-lettering"
```

---

### Task 4: producer-demo trigger/observability endpoints

**Files:**
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event/OrderEventController.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryController.java`
- Create: `communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/exception/DemoExceptionHandler.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/event/OrderEventControllerTest.java`
- Test: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery/DeliveryControllerTest.java`

**Interfaces:**
- Consumes: `SubscriptionService.findByEventType` (Task 2); `WebhookDispatcher.dispatch/deliveries/deadLetters`, `DeliveryAttempt` accessors (Task 3).
- Produces: `POST /orders/{orderId}/events/{eventType}` — accepted, dispatches to every subscription whose `eventTypes` contains `"order." + eventType`.
- Produces: `GET /deliveries`, `GET /deliveries/dead-letter`.

- [ ] **Step 1: Write the failing OrderEventController test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/event/OrderEventControllerTest.java`:

```java
package com.testingai.webhooks.producer.event;

import com.testingai.webhooks.producer.delivery.WebhookDispatcher;
import com.testingai.webhooks.producer.subscription.Subscription;
import com.testingai.webhooks.producer.subscription.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderEventControllerTest {

	private final SubscriptionService subscriptionService = new SubscriptionService();
	private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new OrderEventController(subscriptionService, webhookDispatcher)).build();

	@Test
	void triggerEvent_dispatchesOnlyToSubscriptionsMatchingEventType() throws Exception {
		Subscription matching = subscriptionService.register("http://a", "secret", Set.of("order.created"));
		Subscription nonMatching = subscriptionService.register("http://b", "secret", Set.of("order.paid"));
		when(webhookDispatcher.dispatch(eq(matching), any(OrderEvent.class))).thenReturn("delivery-1");

		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-1", "created"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$[0]").value("delivery-1"));

		verify(webhookDispatcher, times(1)).dispatch(eq(matching), any(OrderEvent.class));
		verify(webhookDispatcher, never()).dispatch(eq(nonMatching), any());
	}

	@Test
	void triggerEvent_prefixesEventTypeWithOrderDot_andSetsOrderId() throws Exception {
		Subscription subscription = subscriptionService.register("http://a", "secret", Set.of("order.shipped"));
		when(webhookDispatcher.dispatch(any(), any())).thenReturn("delivery-2");

		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-2", "shipped"))
				.andExpect(status().isAccepted());

		ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
		verify(webhookDispatcher).dispatch(eq(subscription), captor.capture());
		assertThat(captor.getValue().eventType()).isEqualTo("order.shipped");
		assertThat(captor.getValue().orderId()).isEqualTo("order-2");
	}

	@Test
	void triggerEvent_returnsEmptyList_whenNoSubscriptionsMatch() throws Exception {
		mockMvc.perform(post("/orders/{orderId}/events/{eventType}", "order-3", "cancelled"))
				.andExpect(status().isAccepted()).andExpect(content().json("[]"));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=OrderEventControllerTest`
Expected: FAIL — compilation error, `OrderEventController` does not exist.

- [ ] **Step 3: Implement OrderEventController**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event/OrderEventController.java`:

```java
package com.testingai.webhooks.producer.event;

import com.testingai.webhooks.producer.delivery.WebhookDispatcher;
import com.testingai.webhooks.producer.subscription.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class OrderEventController {

	private final SubscriptionService subscriptionService;
	private final WebhookDispatcher webhookDispatcher;

	public OrderEventController(SubscriptionService subscriptionService, WebhookDispatcher webhookDispatcher) {
		this.subscriptionService = subscriptionService;
		this.webhookDispatcher = webhookDispatcher;
	}

	@PostMapping("/orders/{orderId}/events/{eventType}")
	public ResponseEntity<List<String>> triggerEvent(@PathVariable String orderId, @PathVariable String eventType,
			@RequestBody(required = false) Map<String, Object> data) {
		String fullEventType = "order." + eventType;
		OrderEvent event = new OrderEvent(fullEventType, orderId, Instant.now(), data == null ? Map.of() : data);
		List<String> deliveryIds = subscriptionService.findByEventType(fullEventType).stream()
				.map(subscription -> webhookDispatcher.dispatch(subscription, event)).toList();
		return ResponseEntity.accepted().body(deliveryIds);
	}
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=OrderEventControllerTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Write the failing DeliveryController test**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery/DeliveryControllerTest.java`:

```java
package com.testingai.webhooks.producer.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryControllerTest {

	private final WebhookDispatcher webhookDispatcher = mock(WebhookDispatcher.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DeliveryController(webhookDispatcher))
			.build();

	@Test
	void deliveries_returnsAllDeliveryAttemptsAsViews() throws Exception {
		DeliveryAttempt succeeded = new DeliveryAttempt("d1", "sub-1", "order.created", "{}", "http://a", "secret");
		succeeded.incrementAttemptCount();
		succeeded.markSucceeded();
		when(webhookDispatcher.deliveries()).thenReturn(List.of(succeeded));

		mockMvc.perform(get("/deliveries")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d1")).andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$[0].attemptCount").value(1));
	}

	@Test
	void deadLetters_returnsOnlyDeadLetteredAttempts() throws Exception {
		DeliveryAttempt deadLettered = new DeliveryAttempt("d2", "sub-1", "order.created", "{}", "http://a", "secret");
		deadLettered.incrementAttemptCount();
		deadLettered.markDeadLettered();
		when(webhookDispatcher.deadLetters()).thenReturn(List.of(deadLettered));

		mockMvc.perform(get("/deliveries/dead-letter")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d2"))
				.andExpect(jsonPath("$[0].status").value("DEAD_LETTERED"));
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=DeliveryControllerTest`
Expected: FAIL — compilation error, `DeliveryController` does not exist.

- [ ] **Step 7: Implement DeliveryController and DemoExceptionHandler**

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryController.java`:

```java
package com.testingai.webhooks.producer.delivery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class DeliveryController {

	public record DeliveryView(String deliveryId, String subscriptionId, String eventType, DeliveryStatus status,
			int attemptCount, Instant nextRetryAt) {
	}

	private final WebhookDispatcher webhookDispatcher;

	public DeliveryController(WebhookDispatcher webhookDispatcher) {
		this.webhookDispatcher = webhookDispatcher;
	}

	@GetMapping("/deliveries")
	public List<DeliveryView> deliveries() {
		return webhookDispatcher.deliveries().stream().map(this::toView).toList();
	}

	@GetMapping("/deliveries/dead-letter")
	public List<DeliveryView> deadLetters() {
		return webhookDispatcher.deadLetters().stream().map(this::toView).toList();
	}

	private DeliveryView toView(DeliveryAttempt attempt) {
		return new DeliveryView(attempt.deliveryId(), attempt.subscriptionId(), attempt.eventType(), attempt.status(),
				attempt.attemptCount(), attempt.nextRetryAt());
	}
}
```

`communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/exception/DemoExceptionHandler.java`:

```java
package com.testingai.webhooks.producer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
	}
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test -Dtest=DeliveryControllerTest`
Expected: PASS (2 tests)

- [ ] **Step 9: Run the full producer-demo test suite, including the application context test, to confirm the new beans wire in cleanly**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test`
Expected: PASS (all tests) — `WebhooksProducerDemoApplicationTest.contextLoads` now also verifies `DispatchConfig`'s beans and every controller wire up without circular-dependency or missing-bean errors.

- [ ] **Step 10: Commit**

```bash
git add communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/event/OrderEventController.java communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/delivery/DeliveryController.java communication-protocols/webhooks/producer-demo/src/main/java/com/testingai/webhooks/producer/exception communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/event communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/delivery/DeliveryControllerTest.java
git commit -m "feat(communication-protocols): add event-trigger and delivery-observability endpoints to webhooks-producer-demo"
```

---

### Task 5: producer-demo load tests (Gatling + JMeter)

**Files:**
- Create: `communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/performance/DemoSimulation.java`
- Create: `communication-protocols/webhooks/producer-demo/src/test/jmeter/DemoSimulation.jmx`

**Interfaces:**
- Consumes: `POST /orders/{orderId}/events/{eventType}` (Task 4). No new production interfaces produced.

Both files are excluded from `mvn test` by the inherited surefire `**/performance/**` exclude and the opt-in `jmeter-load-test` profile respectively — there is nothing to TDD here, just author the load scripts and confirm they run against a live instance.

- [ ] **Step 1: Create the Gatling simulation**

`communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/performance/DemoSimulation.java`:

```java
package com.testingai.webhooks.producer.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8096")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("Webhooks Producer Demo")
			.exec(http("Trigger order.created").post("/orders/order-1/events/created").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.paid").post("/orders/order-1/events/paid").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.shipped").post("/orders/order-1/events/shipped").check(status().is(202)))
			.pause(Duration.ofMillis(500))
			.exec(http("Trigger order.cancelled").post("/orders/order-2/events/cancelled").check(status().is(202)));

	{
		// 2 users, ramped a few seconds apart, matching the gRPC/GraphQL demos' pacing style. Run with a healthy
		// consumer-demo and at least one subscription registered so the console logs show real dispatch/success
		// activity, not just 202s with empty delivery-id lists.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
```

- [ ] **Step 2: Create the JMeter test plan**

`communication-protocols/webhooks/producer-demo/src/test/jmeter/DemoSimulation.jmx`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Webhooks Producer Demo">
      <stringProp name="TestPlan.comments">Load test for webhooks-producer-demo. Mirrors com.testingai.webhooks.producer.performance.DemoSimulation (Gatling) with the same requests and pacing, run via JMeter instead. Run with webhooks-consumer-demo up and at least one subscription registered.</stringProp>
      <boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Webhooks Producer Demo Users">
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <intProp name="ThreadGroup.ramp_time">6</intProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller">
          <stringProp name="LoopController.loops">1</stringProp>
          <boolProp name="LoopController.continue_forever">false</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
          <collectionProp name="HeaderManager.headers">
            <elementProp name="" elementType="Header">
              <stringProp name="Header.name">Content-Type</stringProp>
              <stringProp name="Header.value">application/json</stringProp>
            </elementProp>
          </collectionProp>
        </HeaderManager>
        <hashTree/>
        <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Pause Between Calls" enabled="true">
          <stringProp name="ConstantTimer.delay">500</stringProp>
        </ConstantTimer>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Trigger order.created" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8096</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/orders/order-1/events/created</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Trigger order.paid" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8096</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/orders/order-1/events/paid</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Trigger order.shipped" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8096</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/orders/order-1/events/shipped</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Trigger order.cancelled" enabled="true">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8096</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/orders/order-2/events/cancelled</stringProp>
          <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
          <boolProp name="ResultCollector.error_logging">false</boolProp>
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              <message>true</message>
              <threadName>true</threadName>
              <dataType>true</dataType>
              <encoding>false</encoding>
              <assertions>true</assertions>
              <subresults>true</subresults>
              <responseData>false</responseData>
              <samplerData>false</samplerData>
              <xml>false</xml>
              <fieldNames>true</fieldNames>
              <responseHeaders>false</responseHeaders>
              <requestHeaders>false</requestHeaders>
              <responseDataOnError>false</responseDataOnError>
              <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
              <assertionsResultsToSave>0</assertionsResultsToSave>
              <bytes>true</bytes>
              <sentBytes>true</sentBytes>
              <url>true</url>
              <threadCounts>true</threadCounts>
              <idleTime>true</idleTime>
              <connectTime>true</connectTime>
            </value>
          </objProp>
          <stringProp name="filename"></stringProp>
        </ResultCollector>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

- [ ] **Step 3: Verify the Gatling simulation compiles**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo test-compile`
Expected: BUILD SUCCESS (this only compiles the simulation class; it does not execute it, since Gatling is excluded from `mvn test`)

- [ ] **Step 4: Commit**

```bash
git add communication-protocols/webhooks/producer-demo/src/test/java/com/testingai/webhooks/producer/performance communication-protocols/webhooks/producer-demo/src/test/jmeter
git commit -m "test(communication-protocols): add Gatling and JMeter load tests for webhooks-producer-demo"
```

---

### Task 6: consumer-demo scaffold + HmacVerifier

**Files:**
- Create: `communication-protocols/webhooks/consumer-demo/pom.xml`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/resources/application.yml`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/WebhooksConsumerDemoApplication.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/security/HmacVerifier.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/WebhooksConsumerDemoApplicationTest.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/security/HmacVerifierTest.java`

**Interfaces:**
- Produces: `HmacVerifier.verify(String secret, String payload, String signatureHeader) -> boolean` — consumed by `WebhookReceiverController` in Task 8.

- [ ] **Step 1: Create the consumer-demo pom.xml**

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

    <artifactId>webhooks-consumer-demo</artifactId>
    <name>Webhooks Consumer Demo</name>
    <description>Webhook receiver demonstrating HMAC signature verification, delivery-id idempotency, and on-demand failure simulation</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.webhooks.consumer.WebhooksConsumerDemoApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

(No Gatling/JMeter plugin config here — the load test lives in `webhooks-producer-demo` per the module design and drives both apps from the producer side.)

- [ ] **Step 2: Create application.yml**

`communication-protocols/webhooks/consumer-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8097

webhook:
  secret: consumer-demo-secret-change-me
```

- [ ] **Step 3: Create the main application class**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/WebhooksConsumerDemoApplication.java`:

```java
package com.testingai.webhooks.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebhooksConsumerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebhooksConsumerDemoApplication.class, args);
	}
}
```

- [ ] **Step 4: Write the failing HmacVerifier test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/security/HmacVerifierTest.java`:

```java
package com.testingai.webhooks.consumer.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacVerifierTest {

	private final HmacVerifier hmacVerifier = new HmacVerifier();

	@Test
	void verify_returnsTrue_whenSignatureMatches() {
		boolean valid = hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}",
				"sha256=84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");

		assertThat(valid).isTrue();
	}

	@Test
	void verify_returnsFalse_whenSignatureDoesNotMatch() {
		boolean valid = hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}", "sha256=deadbeef");

		assertThat(valid).isFalse();
	}

	@Test
	void verify_returnsFalse_whenSecretDiffers() {
		boolean valid = hmacVerifier.verify("wrong-secret", "{\"hello\":\"world\"}",
				"sha256=84cc33df716ed0b0598f07437c94069ace3730358778a592bd6bbd1423d111f3");

		assertThat(valid).isFalse();
	}

	@Test
	void verify_returnsFalse_whenSignatureHeaderMissing() {
		assertThat(hmacVerifier.verify("test-secret", "{\"hello\":\"world\"}", null)).isFalse();
	}
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=HmacVerifierTest`
Expected: FAIL — compilation error, `HmacVerifier` does not exist.

- [ ] **Step 6: Implement HmacVerifier**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/security/HmacVerifier.java`:

```java
package com.testingai.webhooks.consumer.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HmacVerifier {

	private static final String ALGORITHM = "HmacSHA256";

	public boolean verify(String secret, String payload, String signatureHeader) {
		if (signatureHeader == null) {
			return false;
		}
		String expected = "sha256=" + sign(secret, payload);
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				signatureHeader.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String secret, String payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Unable to compute HMAC signature", e);
		}
	}
}
```

- [ ] **Step 7: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=HmacVerifierTest`
Expected: PASS (4 tests)

- [ ] **Step 8: Write and run the application context test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/WebhooksConsumerDemoApplicationTest.java`:

```java
package com.testingai.webhooks.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebhooksConsumerDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=WebhooksConsumerDemoApplicationTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/webhooks/consumer-demo
git commit -m "feat(communication-protocols): scaffold webhooks-consumer-demo with HmacVerifier"
```

---

### Task 7: consumer-demo on-demand failure simulation

**Files:**
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/failure/FailureSimulationState.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/failure/FailureSimulationController.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/failure/FailureSimulationStateTest.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/failure/FailureSimulationControllerTest.java`

**Interfaces:**
- Produces: `FailureSimulationState.arm(int count)`, `FailureSimulationState.consumeFailure() -> boolean` — consumed by `WebhookReceiverController` in Task 8.
- Produces: `POST /admin/simulate-failures?count=N`.

- [ ] **Step 1: Write the failing FailureSimulationState test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/failure/FailureSimulationStateTest.java`:

```java
package com.testingai.webhooks.consumer.failure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulationStateTest {

	private final FailureSimulationState state = new FailureSimulationState();

	@Test
	void consumeFailure_returnsFalse_whenNotArmed() {
		assertThat(state.consumeFailure()).isFalse();
	}

	@Test
	void consumeFailure_returnsTrueExactlyArmedCountTimes_thenFalse() {
		state.arm(3);

		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isTrue();
		assertThat(state.consumeFailure()).isFalse();
	}

	@Test
	void remaining_reflectsCountdown() {
		state.arm(2);
		assertThat(state.remaining()).isEqualTo(2);

		state.consumeFailure();

		assertThat(state.remaining()).isEqualTo(1);
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=FailureSimulationStateTest`
Expected: FAIL — compilation error, `FailureSimulationState` does not exist.

- [ ] **Step 3: Implement FailureSimulationState**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/failure/FailureSimulationState.java`:

```java
package com.testingai.webhooks.consumer.failure;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FailureSimulationState {

	private final AtomicInteger remainingFailures = new AtomicInteger(0);

	public void arm(int count) {
		remainingFailures.set(count);
	}

	public boolean consumeFailure() {
		return remainingFailures.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0;
	}

	public int remaining() {
		return remainingFailures.get();
	}
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=FailureSimulationStateTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Write the failing FailureSimulationController test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/failure/FailureSimulationControllerTest.java`:

```java
package com.testingai.webhooks.consumer.failure;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FailureSimulationControllerTest {

	private final FailureSimulationState failureSimulationState = new FailureSimulationState();
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new FailureSimulationController(failureSimulationState)).build();

	@Test
	void simulateFailures_armsStateWithGivenCount() throws Exception {
		mockMvc.perform(post("/admin/simulate-failures").param("count", "3")).andExpect(status().isAccepted());

		assertThat(failureSimulationState.remaining()).isEqualTo(3);
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=FailureSimulationControllerTest`
Expected: FAIL — compilation error, `FailureSimulationController` does not exist.

- [ ] **Step 7: Implement FailureSimulationController**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/failure/FailureSimulationController.java`:

```java
package com.testingai.webhooks.consumer.failure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class FailureSimulationController {

	private final FailureSimulationState failureSimulationState;

	public FailureSimulationController(FailureSimulationState failureSimulationState) {
		this.failureSimulationState = failureSimulationState;
	}

	@PostMapping("/simulate-failures")
	public ResponseEntity<Void> simulateFailures(@RequestParam int count) {
		failureSimulationState.arm(count);
		return ResponseEntity.accepted().build();
	}
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=FailureSimulationControllerTest`
Expected: PASS (1 test)

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/failure communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/failure
git commit -m "feat(communication-protocols): add on-demand failure simulation to webhooks-consumer-demo"
```

---

### Task 8: consumer-demo receiving endpoint (verification, dedup, admin inspection)

**Files:**
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/ReceivedEvent.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/IncomingOrderEvent.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/ReceivedEventStore.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/WebhookReceiverController.java`
- Create: `communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/admin/AdminController.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/receiver/ReceivedEventStoreTest.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/receiver/WebhookReceiverControllerTest.java`
- Test: `communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/admin/AdminControllerTest.java`

**Interfaces:**
- Consumes: `HmacVerifier.verify` (Task 6); `FailureSimulationState.consumeFailure` (Task 7).
- Produces: `record ReceivedEvent(String deliveryId, String eventType, String orderId, Instant receivedAt, boolean duplicate)`.
- Produces: `ReceivedEventStore.recordIfNew(String deliveryId, String eventType, String orderId) -> boolean` (true = first time seen), `ReceivedEventStore.all() -> List<ReceivedEvent>` — consumed by `AdminController`.
- Produces: `POST /webhooks/orders`, `GET /admin/received`.

- [ ] **Step 1: Write the failing ReceivedEventStore test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/receiver/ReceivedEventStoreTest.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivedEventStoreTest {

	private final ReceivedEventStore store = new ReceivedEventStore();

	@Test
	void recordIfNew_returnsTrue_andRecordsNonDuplicate_forFirstOccurrence() {
		boolean isNew = store.recordIfNew("d1", "order.created", "order-1");

		assertThat(isNew).isTrue();
		assertThat(store.all()).extracting(ReceivedEvent::duplicate).containsExactly(false);
	}

	@Test
	void recordIfNew_returnsFalse_andRecordsDuplicate_forRepeatedDeliveryId() {
		store.recordIfNew("d1", "order.created", "order-1");

		boolean isNew = store.recordIfNew("d1", "order.created", "order-1");

		assertThat(isNew).isFalse();
		assertThat(store.all()).extracting(ReceivedEvent::duplicate).containsExactly(false, true);
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=ReceivedEventStoreTest`
Expected: FAIL — compilation error, `ReceivedEventStore`/`ReceivedEvent` do not exist.

- [ ] **Step 3: Implement ReceivedEvent and ReceivedEventStore**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/ReceivedEvent.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import java.time.Instant;

public record ReceivedEvent(String deliveryId, String eventType, String orderId, Instant receivedAt,
		boolean duplicate) {
}
```

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/ReceivedEventStore.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ReceivedEventStore {

	private final Set<String> seenDeliveryIds = ConcurrentHashMap.newKeySet();
	private final List<ReceivedEvent> events = new CopyOnWriteArrayList<>();

	public boolean recordIfNew(String deliveryId, String eventType, String orderId) {
		boolean isNew = seenDeliveryIds.add(deliveryId);
		events.add(new ReceivedEvent(deliveryId, eventType, orderId, Instant.now(), !isNew));
		return isNew;
	}

	public List<ReceivedEvent> all() {
		return List.copyOf(events);
	}
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=ReceivedEventStoreTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Write the failing WebhookReceiverController test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/receiver/WebhookReceiverControllerTest.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.webhooks.consumer.failure.FailureSimulationState;
import com.testingai.webhooks.consumer.security.HmacVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookReceiverControllerTest {

	private static final String SECRET = "test-secret";
	private static final String BODY = "{\"eventType\":\"order.created\",\"orderId\":\"order-1\","
			+ "\"occurredAt\":\"2026-01-01T00:00:00Z\",\"data\":{}}";
	private static final String VALID_SIGNATURE = "sha256="
			+ "77fce76c2cc7b5e4ba92a48bf14b80408e44d0d7caf9d5c8b2054c6df246ac54";

	private final HmacVerifier hmacVerifier = new HmacVerifier();
	private final FailureSimulationState failureSimulationState = new FailureSimulationState();
	private final ReceivedEventStore receivedEventStore = new ReceivedEventStore();
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebhookReceiverController(hmacVerifier,
			failureSimulationState, receivedEventStore, objectMapper, SECRET)).build();

	@Test
	void receive_returns200_andRecordsEvent_whenSignatureValid() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON)
				.header("X-Webhook-Id", "d1").header("X-Webhook-Event", "order.created")
				.header("X-Webhook-Signature", VALID_SIGNATURE).content(BODY)).andExpect(status().isOk());

		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::deliveryId).containsExactly("d1");
		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::duplicate).containsExactly(false);
	}

	@Test
	void receive_returns401_andDoesNotRecord_whenSignatureInvalid() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON)
				.header("X-Webhook-Id", "d2").header("X-Webhook-Event", "order.created")
				.header("X-Webhook-Signature", "sha256=deadbeef").content(BODY))
				.andExpect(status().isUnauthorized());

		assertThat(receivedEventStore.all()).isEmpty();
	}

	@Test
	void receive_returns500_andDoesNotRecord_whenFailureSimulationArmed() throws Exception {
		failureSimulationState.arm(1);

		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON)
				.header("X-Webhook-Id", "d3").header("X-Webhook-Event", "order.created")
				.header("X-Webhook-Signature", VALID_SIGNATURE).content(BODY))
				.andExpect(status().isInternalServerError());

		assertThat(receivedEventStore.all()).isEmpty();
		assertThat(failureSimulationState.remaining()).isEqualTo(0);
	}

	@Test
	void receive_marksSecondDeliveryOfSameId_asDuplicate() throws Exception {
		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON)
				.header("X-Webhook-Id", "d4").header("X-Webhook-Event", "order.created")
				.header("X-Webhook-Signature", VALID_SIGNATURE).content(BODY)).andExpect(status().isOk());

		mockMvc.perform(post("/webhooks/orders").contentType(MediaType.APPLICATION_JSON)
				.header("X-Webhook-Id", "d4").header("X-Webhook-Event", "order.created")
				.header("X-Webhook-Signature", VALID_SIGNATURE).content(BODY)).andExpect(status().isOk());

		assertThat(receivedEventStore.all()).extracting(ReceivedEvent::duplicate).containsExactly(false, true);
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=WebhookReceiverControllerTest`
Expected: FAIL — compilation error, `WebhookReceiverController`/`IncomingOrderEvent` do not exist.

- [ ] **Step 7: Implement IncomingOrderEvent and WebhookReceiverController**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/IncomingOrderEvent.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import java.time.Instant;
import java.util.Map;

public record IncomingOrderEvent(String eventType, String orderId, Instant occurredAt, Map<String, Object> data) {
}
```

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver/WebhookReceiverController.java`:

```java
package com.testingai.webhooks.consumer.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.webhooks.consumer.failure.FailureSimulationState;
import com.testingai.webhooks.consumer.security.HmacVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class WebhookReceiverController {

	private final HmacVerifier hmacVerifier;
	private final FailureSimulationState failureSimulationState;
	private final ReceivedEventStore receivedEventStore;
	private final ObjectMapper objectMapper;
	private final String secret;

	public WebhookReceiverController(HmacVerifier hmacVerifier, FailureSimulationState failureSimulationState,
			ReceivedEventStore receivedEventStore, ObjectMapper objectMapper,
			@Value("${webhook.secret}") String secret) {
		this.hmacVerifier = hmacVerifier;
		this.failureSimulationState = failureSimulationState;
		this.receivedEventStore = receivedEventStore;
		this.objectMapper = objectMapper;
		this.secret = secret;
	}

	@PostMapping(value = "/webhooks/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> receive(@RequestHeader("X-Webhook-Id") String deliveryId,
			@RequestHeader("X-Webhook-Event") String eventType,
			@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
			@RequestBody String rawBody) throws IOException {
		if (failureSimulationState.consumeFailure()) {
			return ResponseEntity.internalServerError().build();
		}
		if (!hmacVerifier.verify(secret, rawBody, signature)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		IncomingOrderEvent event = objectMapper.readValue(rawBody, IncomingOrderEvent.class);
		receivedEventStore.recordIfNew(deliveryId, eventType, event.orderId());
		return ResponseEntity.ok().build();
	}
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=WebhookReceiverControllerTest`
Expected: PASS (4 tests)

- [ ] **Step 9: Write the failing AdminController test**

`communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/admin/AdminControllerTest.java`:

```java
package com.testingai.webhooks.consumer.admin;

import com.testingai.webhooks.consumer.receiver.ReceivedEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

	private final ReceivedEventStore receivedEventStore = new ReceivedEventStore();
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(receivedEventStore)).build();

	@Test
	void received_returnsAllRecordedEvents() throws Exception {
		receivedEventStore.recordIfNew("d1", "order.created", "order-1");

		mockMvc.perform(get("/admin/received")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].deliveryId").value("d1")).andExpect(jsonPath("$[0].orderId").value("order-1"))
				.andExpect(jsonPath("$[0].duplicate").value(false));
	}
}
```

- [ ] **Step 10: Run it to verify it fails**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=AdminControllerTest`
Expected: FAIL — compilation error, `AdminController` does not exist.

- [ ] **Step 11: Implement AdminController**

`communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/admin/AdminController.java`:

```java
package com.testingai.webhooks.consumer.admin;

import com.testingai.webhooks.consumer.receiver.ReceivedEvent;
import com.testingai.webhooks.consumer.receiver.ReceivedEventStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

	private final ReceivedEventStore receivedEventStore;

	public AdminController(ReceivedEventStore receivedEventStore) {
		this.receivedEventStore = receivedEventStore;
	}

	@GetMapping("/received")
	public List<ReceivedEvent> received() {
		return receivedEventStore.all();
	}
}
```

- [ ] **Step 12: Run it to verify it passes**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test -Dtest=AdminControllerTest`
Expected: PASS (1 test)

- [ ] **Step 13: Run the full consumer-demo test suite, including the application context test**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo test`
Expected: PASS (all tests) — `WebhooksConsumerDemoApplicationTest.contextLoads` confirms `WebhookReceiverController`'s `@Value("${webhook.secret}")` resolves against `application.yml` and every bean wires up cleanly.

- [ ] **Step 14: Commit**

```bash
git add communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/receiver communication-protocols/webhooks/consumer-demo/src/main/java/com/testingai/webhooks/consumer/admin communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/receiver communication-protocols/webhooks/consumer-demo/src/test/java/com/testingai/webhooks/consumer/admin
git commit -m "feat(communication-protocols): add webhook receiving, verification, dedup, and admin inspection to webhooks-consumer-demo"
```

---

### Task 9: documentation (CLAUDE.md, communication-protocols/README.md, webhooks/README.md)

**Files:**
- Modify: `CLAUDE.md`
- Modify: `communication-protocols/README.md`
- Create: `communication-protocols/webhooks/README.md`

**Interfaces:** None — documentation only, no code.

- [ ] **Step 1: Add a "Webhooks communication protocol demo" command section to CLAUDE.md**

Insert immediately after the existing GraphQL command section (which ends with the `mvn verify -Pjmeter-load-test -pl graphql/spring-demo` line and its closing ` ``` `), immediately before the `### Camunda workflow engine demo` heading:

Heading line (plain markdown, not fenced):

```
### Webhooks communication protocol demo (run from the reactor root, no docker infrastructure required)
```

Followed by this fenced command block:

```bash
cd communication-protocols

mvn clean package                                                      # build both apps (reactor build)
mvn test -pl webhooks/producer-demo,webhooks/consumer-demo              # unit tests for both modules (Gatling excluded automatically)
mvn test -pl webhooks/consumer-demo -Dtest=ClassName                    # single test class
mvn -pl webhooks/consumer-demo spring-boot:run                          # run the consumer first (receives on :8097)
mvn -pl webhooks/producer-demo spring-boot:run                          # then the producer (dispatches from :8096)
mvn gatling:test -pl webhooks/producer-demo                             # Gatling load test — requires both apps running first
mvn verify -Pjmeter-load-test -pl webhooks/producer-demo                # JMeter load test — requires both apps running first
```

- [ ] **Step 2: Add two rows to the repository layout table in CLAUDE.md**

Find this existing row:

```markdown
| `communication-protocols/graphql/spring-demo/` | GraphQL demo — single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns against a Products↔Reviews domain — no external infrastructure required |
```

Add these two rows immediately after it:

```markdown
| `communication-protocols/webhooks/producer-demo/` | Webhooks demo — subscription registry, HMAC-signed dispatch, retry/backoff, dead-lettering — no external infrastructure required |
| `communication-protocols/webhooks/consumer-demo/` | Webhooks demo — signature verification, delivery-id idempotency/dedup, on-demand failure simulation — no external infrastructure required |
```

- [ ] **Step 3: Add a Webhooks row to communication-protocols/README.md**

Find:

```markdown
| [GraphQL](graphql/) | Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns | Client-driven field selection over one endpoint; aggregating/relational data from a single request |

More protocol demos may be added here over time (e.g. WebSocket).
```

Replace with:

```markdown
| [GraphQL](graphql/) | Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns | Client-driven field selection over one endpoint; aggregating/relational data from a single request |
| [Webhooks](webhooks/) | Two independent Spring Boot apps — producer (subscriptions, HMAC-signed dispatch, retry/backoff, dead-lettering) + consumer (verification, idempotency/dedup) | Async server-to-server push where the receiver can't poll; event notifications between independently-owned systems |

More protocol demos may be added here over time (e.g. WebSocket).
```

- [ ] **Step 4: Create communication-protocols/webhooks/README.md**

(Outer fence below uses four backticks specifically because the file's own content contains three-backtick ```bash blocks — a three-backtick outer fence would close prematurely at the first one.)

````markdown
# Webhooks Demo

Demonstrates webhooks — asynchronous server-to-server push over plain HTTP, where the receiver registers a callback URL once and the sender calls it whenever an event happens, instead of the receiver polling for changes — via two independent Spring Boot apps:

- **[producer-demo](producer-demo/)** — owns the subscription registry and the `WebhookDispatcher`: signs every delivery with HMAC-SHA256, retries failures with exponential backoff, and dead-letters deliveries that exhaust their retry budget.
- **[consumer-demo](consumer-demo/)** — receives deliveries, verifies the HMAC signature, deduplicates by delivery id, and exposes an admin endpoint to arm deterministic failures so retry/backoff/dead-letter behavior is observable on demand.

Unlike gRPC's client/server split (both apps speak the same generated stub), producer and consumer here only ever talk plain signed JSON over HTTP — either side could be replaced with a service in any language without touching the other.

## The five patterns

| Pattern | Where | What it demonstrates |
|---|---|---|
| Subscription registration | producer-demo `POST /subscriptions` | A consumer registers a callback URL, shared secret, and event types before any delivery starts |
| HMAC signature verification | producer signs, consumer verifies | The receiver proves the payload actually came from the sender and wasn't tampered with in transit |
| Retry with exponential backoff | producer-demo `WebhookDispatcher` | A failed delivery is retried with increasing delay instead of dropped or hammering the receiver |
| Dead-lettering | producer-demo `WebhookDispatcher` | After exhausting retries, the delivery is parked for inspection instead of retried forever |
| Idempotency / dedup | consumer-demo `WebhookReceiverController` | A retried delivery (same `X-Webhook-Id`) is recognized and not reprocessed — the flip side of at-least-once delivery |

### Subscription registration

**Pros**
- Receivers opt in only to the event types they care about
- No polling — the producer decides when to push
- Multiple independent subscribers can register against the same event stream

**Cons**
- The producer must track subscriber state (persisted in real systems; in-memory here)
- A subscriber must expose a publicly reachable HTTP endpoint
- No discovery mechanism — the subscriber must already know the producer's registration endpoint

**Typical use cases**
- Payment gateways notifying merchants of charge/refund events
- CI providers notifying external tools of build status
- SaaS platforms notifying integrations of resource changes

### HMAC signature verification

**Pros**
- Receiver can cryptographically prove the payload came from the real producer and wasn't altered in transit
- No shared session/token needed per request — just a static secret
- Cheap to compute compared to asymmetric signing

**Cons**
- Secret must be provisioned out-of-band and kept in sync on both sides
- No secret rotation story here — a deliberate demo simplification
- Doesn't protect against replay by itself — only tampering/spoofing (see idempotency below)

**Typical use cases**
- Every major webhook provider (GitHub, Stripe, Slack) signs payloads this way
- Any server-to-server callback over the public internet where the receiver can't otherwise trust the caller

### Retry with exponential backoff

**Pros**
- Transient receiver outages (deploys, brief overload) don't lose events
- Exponential spacing avoids hammering a struggling receiver
- Bounded attempt count keeps a single failing delivery from retrying forever

**Cons**
- Delivery is delayed, sometimes significantly, when the receiver is down
- The producer must hold delivery state until it succeeds or exhausts retries
- Retried deliveries can arrive out of order relative to newer events

**Typical use cases**
- Any at-least-once webhook delivery guarantee
- Recovering from a receiver's short maintenance window without manual intervention

### Dead-lettering

**Pros**
- A permanently failing delivery is parked for inspection instead of retried forever or silently dropped
- Operators can see exactly what failed and why
- Keeps the retry scheduler from accumulating unbounded pending work

**Cons**
- Dead-lettered events need a manual (or separately built) replay path to actually recover — not built here
- Still requires an operator to notice and act — this demo has no alerting
- Represents a genuine loss of real-time delivery for that event

**Typical use cases**
- The DLQ pattern from message brokers, applied to webhook delivery instead of message consumption
- Surfacing integration failures (e.g. a customer's misconfigured endpoint) for support follow-up

### Idempotency / dedup

**Pros**
- Safe to retry aggressively on the producer side without the receiver double-processing
- Protects against duplicate delivery from any source, not just this producer's own retries
- Simple to implement — one id per delivery, one seen-set on the receiver

**Cons**
- Receiver must remember every delivery id it's seen (unbounded here — a demo simplification; production systems expire old ids)
- Only works if the producer's delivery id stays stable across retries of the *same* logical delivery
- Doesn't address ordering — a duplicate is recognized, but out-of-order delivery is a separate concern

**Typical use cases**
- Any at-least-once delivery system paired with retries (which is: almost all webhook providers)
- Payment/order webhooks specifically, where double-processing has real consequences

## Prerequisites

Java 21, Maven. No Docker.

## Run

Consumer must be up first so it's ready to receive (registration itself is just data, so this ordering is about being ready to receive, not a hard requirement):

```bash
cd communication-protocols
mvn -pl webhooks/consumer-demo spring-boot:run
```

In a second terminal:

```bash
cd communication-protocols
mvn -pl webhooks/producer-demo spring-boot:run
```

## Walkthrough

**1. Register a subscription** (consumer-demo's callback URL, with the secret matching its `webhook.secret` in `application.yml`):

```bash
curl -s -X POST http://localhost:8096/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"callbackUrl":"http://localhost:8097/webhooks/orders","secret":"consumer-demo-secret-change-me","eventTypes":["order.created","order.paid"]}'
```

**2. Trigger an event and watch it succeed:**

```bash
curl -s -X POST http://localhost:8096/orders/order-123/events/created
curl -s http://localhost:8096/deliveries
```

The delivery shows `"status":"SUCCEEDED"` after a moment; `curl -s http://localhost:8097/admin/received` shows it recorded with `"duplicate":false`.

**3. Arm 3 deterministic failures on the consumer, then trigger another event:**

```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=3"
curl -s -X POST http://localhost:8096/orders/order-124/events/created
```

Watch producer-demo's console: attempts 1–3 fail, retrying after 1s/2s/4s, then attempt 4 succeeds. `curl -s http://localhost:8096/deliveries` shows `"attemptCount":4,"status":"SUCCEEDED"`.

**4. Arm more failures than the retry budget allows, to see dead-lettering:**

```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=10"
curl -s -X POST http://localhost:8096/orders/order-125/events/created
```

After 5 failed attempts (backoff of 1s/2s/4s/8s/16s — this step takes about 31 seconds to fully resolve), `curl -s http://localhost:8096/deliveries/dead-letter` shows the delivery parked with `"status":"DEAD_LETTERED"`.

**5. Demonstrate dedup by replaying a delivery directly at the consumer.** Copy the exact `X-Webhook-Id`, `X-Webhook-Signature`, and body from step 2's successful delivery out of producer-demo's console log, then:

```bash
curl -s -i -X POST http://localhost:8097/webhooks/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Webhook-Id: <same-delivery-id-as-step-2>' \
  -H 'X-Webhook-Event: order.created' \
  -H 'X-Webhook-Signature: <same-signature-as-step-2>' \
  -d '<same-body-as-step-2>'
```

`curl -s http://localhost:8097/admin/received` now shows two entries for that delivery id — the first with `"duplicate":false`, the second `"duplicate":true`.

## Scope limits

- No persistence — everything is in-memory, single-instance only.
- One secret per subscription, HMAC-SHA256 only — no algorithm negotiation, no secret rotation.
- Retry backoff (1s/2s/4s/8s/16s, 5 attempts) is fixed, not configurable via `application.yml`.
- No manual dead-letter replay endpoint.

## Build & test

```bash
cd communication-protocols
mvn test -pl webhooks/producer-demo,webhooks/consumer-demo
mvn gatling:test -pl webhooks/producer-demo               # Gatling load test — requires both apps running first
mvn verify -Pjmeter-load-test -pl webhooks/producer-demo   # JMeter load test — requires both apps running first
```
````

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md communication-protocols/README.md communication-protocols/webhooks/README.md
git commit -m "docs(communication-protocols): document the webhooks demo"
```

---

### Task 10: end-to-end manual verification

**Files:** None — this task runs the real apps and confirms the documented walkthrough actually behaves as described. No code changes unless a real bug is found, in which case fix it in the relevant task's files and re-run the affected step.

**Interfaces:** None.

- [ ] **Step 1: Full reactor build**

Run: `cd communication-protocols && mvn clean package`
Expected: BUILD SUCCESS for every module, including `webhooks-producer-demo` and `webhooks-consumer-demo`.

- [ ] **Step 2: Full reactor test run**

Run: `cd communication-protocols && mvn test`
Expected: BUILD SUCCESS, all tests pass across every module (Gatling classes excluded automatically).

- [ ] **Step 3: Start consumer-demo in the background**

Run: `cd communication-protocols && mvn -pl webhooks/consumer-demo spring-boot:run > /tmp/webhooks-consumer.log 2>&1 &`
Wait for `Started WebhooksConsumerDemoApplication` in `/tmp/webhooks-consumer.log`, then confirm it's listening:
Run: `curl -s -o /dev/null -w '%{http_code}' http://localhost:8097/admin/received`
Expected: `200`

- [ ] **Step 4: Start producer-demo in the background**

Run: `cd communication-protocols && mvn -pl webhooks/producer-demo spring-boot:run > /tmp/webhooks-producer.log 2>&1 &`
Wait for `Started WebhooksProducerDemoApplication` in `/tmp/webhooks-producer.log`, then confirm it's listening:
Run: `curl -s -o /dev/null -w '%{http_code}' http://localhost:8096/deliveries`
Expected: `200`

- [ ] **Step 5: Register a subscription and confirm it's stored**

Run:
```bash
curl -s -X POST http://localhost:8096/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"callbackUrl":"http://localhost:8097/webhooks/orders","secret":"consumer-demo-secret-change-me","eventTypes":["order.created","order.paid"]}'
```
Expected: JSON body with a generated `id`, `callbackUrl` echoed back, `eventTypes` containing both entries. HTTP status `201` (check via `-i`).

- [ ] **Step 6: Trigger a happy-path event and confirm success end-to-end**

Run: `curl -s -X POST http://localhost:8096/orders/order-123/events/created`
Expected: `202` with a JSON array containing one delivery id.

Run (after ~1 second): `curl -s http://localhost:8096/deliveries`
Expected: one entry with `"status":"SUCCEEDED"`, `"attemptCount":1`.

Run: `curl -s http://localhost:8097/admin/received`
Expected: one entry with the matching `deliveryId`, `"orderId":"order-123"`, `"duplicate":false`.

- [ ] **Step 7: Arm 3 failures and confirm retry/backoff behavior**

Run:
```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=3"
curl -s -X POST http://localhost:8096/orders/order-124/events/created
```
Wait ~8 seconds (1s + 2s + 4s backoff between the 4 attempts), then run: `curl -s http://localhost:8096/deliveries`
Expected: the `order-124` delivery shows `"status":"SUCCEEDED"`, `"attemptCount":4`.

Check `/tmp/webhooks-producer.log` for `retrying at` log lines confirming 3 retries were scheduled before success.

- [ ] **Step 8: Arm more failures than the retry budget and confirm dead-lettering**

Run:
```bash
curl -s -X POST "http://localhost:8097/admin/simulate-failures?count=10"
curl -s -X POST http://localhost:8096/orders/order-125/events/created
```
Wait ~31 seconds (1s+2s+4s+8s+16s across 5 attempts), then run: `curl -s http://localhost:8096/deliveries/dead-letter`
Expected: the `order-125` delivery present with `"status":"DEAD_LETTERED"`, `"attemptCount":5`.

- [ ] **Step 9: Confirm signature rejection**

Run:
```bash
curl -s -i -X POST http://localhost:8097/webhooks/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Webhook-Id: manual-test-1' \
  -H 'X-Webhook-Event: order.created' \
  -H 'X-Webhook-Signature: sha256=deadbeef' \
  -d '{"eventType":"order.created","orderId":"order-999","occurredAt":"2026-01-01T00:00:00Z","data":{}}'
```
Expected: `HTTP/1.1 401`

- [ ] **Step 10: Confirm dedup by replaying the order-123 delivery**

Copy the exact `X-Webhook-Id` and `X-Webhook-Signature` values for `order-123` from `/tmp/webhooks-producer.log` (search for `delivery ... succeeded on attempt 1`, then find the corresponding request in the log or re-derive from `/deliveries`), then replay the identical request (same id, signature, and body) directly against the consumer.
Run: `curl -s http://localhost:8097/admin/received`
Expected: two entries for that delivery id — the first `"duplicate":false`, the second `"duplicate":true`.

- [ ] **Step 11: Stop both apps**

Run: `pkill -f WebhooksProducerDemoApplication; pkill -f WebhooksConsumerDemoApplication`
Expected: both background `spring-boot:run` processes terminate; confirm with `jobs` or `ps aux | grep -i webhooks` showing nothing left running.

- [ ] **Step 12: Final commit (only if Step 1–10 required a fix)**

If every step passed with no code changes, there is nothing to commit — the plan is complete as of Task 9's commit. If a real bug was found and fixed:

```bash
git add -A
git commit -m "fix(communication-protocols): <describe the end-to-end issue found and fixed>"
```
