# Axon Framework CQRS/Event Sourcing Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `cqrs-event-sourcing/axon/spring-demo` Spring Boot module demonstrating CQRS/event-sourcing fundamentals (commands, event-sourced aggregate, decoupled query model, event replay, snapshotting) on top of a real Axon Server instance.

**Architecture:** A single `Order` aggregate (`command/`) emits domain events (`event/`) that are sourced back into aggregate state and asynchronously projected into an in-memory read model (`query/`). A `replay/` service resets the projection's tracking processor to rebuild it from Axon Server's event history. A `config/` bean wires event-count-based snapshotting onto the aggregate. `controller/DemoController` exposes everything over REST, following this repo's established `DemoController` convention.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Axon Framework 4.13.1 (`axon-spring-boot-starter`, `axon-test`), Axon Server (Docker), Lombok, springdoc-openapi, JUnit 5 + Mockito + AssertJ, Gatling.

## Global Constraints

- Java 21, Spring Boot 3.4.4 (matches every other module in this repo).
- `FailureSimulator` must use `FAILURE_RATE = 0.05` and expose `maybeThrow(String context)` — no `shouldFail()` boolean method (per `.claude/rules/code-review.md`).
- Prefer records, pattern matching, switch expressions, text blocks, `SequencedCollection`, virtual threads over pre-Java-21 idioms, on any line this plan adds.
- No explicit `.toString()` on values passed to SLF4J `{}` placeholders or string concatenation.
- All instance fields assigned once must be `private final`, except fields assigned in `@PostConstruct`/`ApplicationRunner.run()`/`@PreDestroy`, which must still be `private`.
- Run `mvn fmt:format` equivalent — this module uses `spotless:apply` (already wired via the parent POM's git hook), not Google Java Format; do not hand-format, let Spotless do it on commit.
- New top-level Maven reactor `cqrs-event-sourcing/` is independent of `message-brokers/` and `noSQL/` (no root aggregator POM exists in this repo — confirmed: there is no `pom.xml` at repo root).

---

### Task 1: Scaffold the `cqrs-event-sourcing/` Maven reactor and `axon-demo` module skeleton

**Files:**
- Create: `cqrs-event-sourcing/pom.xml`
- Create: `cqrs-event-sourcing/eclipse-formatter.xml`
- Create: `cqrs-event-sourcing/axon/spring-demo/pom.xml`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/AxonDemoApplication.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/resources/application.yml`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/AxonDemoApplicationTest.java`

**Interfaces:**
- Produces: `com.testingai.axon.AxonDemoApplication` (Spring Boot main class), Maven coordinates `com.testingai:axon-demo` under parent `com.testingai:cqrs-event-sourcing:1.0.0`, server port `8085`, Axon Server target `localhost:8124`.

- [ ] **Step 1: Create the parent POM**

`cqrs-event-sourcing/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.4</version>
    </parent>

    <groupId>com.testingai</groupId>
    <artifactId>cqrs-event-sourcing</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>CQRS / Event Sourcing</name>
    <description>Parent POM for all CQRS/event-sourcing framework demo modules</description>

    <modules>
        <module>axon/spring-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <springdoc.version>2.8.6</springdoc.version>
        <axon.version>4.13.1</axon.version>
        <gatling.version>3.13.1</gatling.version>
        <gatling-maven-plugin.version>4.15.0</gatling-maven-plugin.version>
        <spotless.version>2.43.0</spotless.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-Dnet.bytebuddy.experimental=true</argLine>
                    <excludes>
                        <exclude>**/performance/**</exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <version>${gatling-maven-plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>install-git-hooks</id>
                        <phase>initialize</phase>
                        <goals>
                            <goal>exec</goal>
                        </goals>
                        <configuration>
                            <executable>git</executable>
                            <arguments>
                                <argument>config</argument>
                                <argument>core.hooksPath</argument>
                                <argument>.githooks</argument>
                            </arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>${spotless.version}</version>
                <configuration>
                    <java>
                        <eclipse>
                            <version>4.31</version>
                            <file>${maven.multiModuleProjectDirectory}/eclipse-formatter.xml</file>
                        </eclipse>
                    </java>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Copy the Eclipse formatter config**

`cqrs-event-sourcing/eclipse-formatter.xml` — identical content to `noSQL/eclipse-formatter.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<profiles version="21">
    <profile kind="CodeFormatterProfile" name="techmix" version="21">
        <!-- Line width: 120 characters -->
        <setting id="org.eclipse.jdt.core.formatter.lineSplit" value="120"/>
        <!-- Javadoc comment line width -->
        <setting id="org.eclipse.jdt.core.formatter.comment.line_length" value="120"/>
        <!-- Keep Javadoc formatting enabled -->
        <setting id="org.eclipse.jdt.core.formatter.comment.format_javadoc_comments" value="true"/>
        <setting id="org.eclipse.jdt.core.formatter.comment.format_block_comments" value="true"/>
        <!-- Indent with tabs (Eclipse default) -->
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="tab"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="4"/>
        <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="4"/>
    </profile>
</profiles>
```

- [ ] **Step 3: Create the `axon-demo` module POM**

`cqrs-event-sourcing/axon/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>cqrs-event-sourcing</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>axon-demo</artifactId>
    <name>Axon Demo</name>
    <description>Learning and demonstration project for Axon Framework CQRS/event-sourcing patterns</description>

    <dependencies>
        <dependency>
            <groupId>org.axonframework</groupId>
            <artifactId>axon-spring-boot-starter</artifactId>
            <version>${axon.version}</version>
        </dependency>
        <dependency>
            <groupId>org.axonframework</groupId>
            <artifactId>axon-test</artifactId>
            <version>${axon.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.axon.AxonDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.axon.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create the Spring Boot main class**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/AxonDemoApplication.java`:

```java
package com.testingai.axon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AxonDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AxonDemoApplication.class, args);
	}
}
```

- [ ] **Step 5: Create `application.yml`**

`cqrs-event-sourcing/axon/spring-demo/src/main/resources/application.yml`:

```yaml
axon:
  axonserver:
    servers: localhost:8124
  eventhandling:
    processors:
      order-projection:
        mode: tracking

server:
  port: 8085
```

- [ ] **Step 6: Write the application smoke test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/AxonDemoApplicationTest.java` — mirrors `MongoDbDemoApplicationTest`, instantiating the class directly so the test never needs a live Axon Server connection:

```java
package com.testingai.axon;

import org.junit.jupiter.api.Test;

class AxonDemoApplicationTest {

	@Test
	void mainClassExists() {
		new AxonDemoApplication();
	}
}
```

- [ ] **Step 7: Build the reactor**

Run: `cd cqrs-event-sourcing && mvn clean package`
Expected: `BUILD SUCCESS`, with `AxonDemoApplicationTest` reported as passing in the `axon-demo` module's surefire output.

- [ ] **Step 8: Commit**

```bash
git add cqrs-event-sourcing/pom.xml cqrs-event-sourcing/eclipse-formatter.xml \
  cqrs-event-sourcing/axon/spring-demo/pom.xml \
  cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/AxonDemoApplication.java \
  cqrs-event-sourcing/axon/spring-demo/src/main/resources/application.yml \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/AxonDemoApplicationTest.java
git commit -m "feat(axon): scaffold cqrs-event-sourcing reactor and axon-demo module"
```

---

### Task 2: Axon Server Docker infrastructure

**Files:**
- Create: `cqrs-event-sourcing/axon/docker/docker-compose.yml`

**Interfaces:**
- Produces: a running Axon Server reachable at gRPC `localhost:8124` (client connections) and dashboard/HTTP `localhost:8024` — required by every later task that runs the app against a real event store (Task 14), but not by any unit test task (3, 5, 6, 7, 8, 9).

- [ ] **Step 1: Write the compose file**

`cqrs-event-sourcing/axon/docker/docker-compose.yml`:

```yaml
name: axon-server

services:
  axonserver:
    image: axoniq/axonserver:latest
    hostname: axonserver
    container_name: axonserver
    ports:
      - "8024:8024"
      - "8124:8124"
    environment:
      - AXONIQ_AXONSERVER_STANDALONE=true
    volumes:
      - axonserver-data:/data
      - axonserver-events:/eventdata

volumes:
  axonserver-data:
  axonserver-events:
```

- [ ] **Step 2: Start it and verify from the host**

Run: `docker compose -f cqrs-event-sourcing/axon/docker/docker-compose.yml up -d`
Wait ~15s for startup, then run: `curl -f http://localhost:8024/actuator/health`
Expected: HTTP 200 with a JSON body containing `"status":"UP"`. (The container image is a minimal JRE image without `curl`/`wget`, so this check is run from the host, not via a Compose `healthcheck:` block — no other service in this compose file depends on Axon Server's readiness, since this repo always runs its Spring Boot demo apps with `mvn spring-boot:run` on the host, never inside Compose.)

Also open `http://localhost:8024` in a browser to confirm the Axon Server dashboard loads.

- [ ] **Step 3: Commit**

```bash
git add cqrs-event-sourcing/axon/docker/docker-compose.yml
git commit -m "feat(axon): add Axon Server docker-compose infrastructure"
```

---

### Task 3: `FailureSimulator` utility

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/util/FailureSimulator.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/util/FailureSimulatorTest.java`

**Interfaces:**
- Produces: `FailureSimulator.maybeThrow(String context)` — static, throws `RuntimeException` ~5% of the time with a message containing `context`. Consumed by `OrderAggregate` in Task 5.

- [ ] **Step 1: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/util/FailureSimulatorTest.java` (mirrors `message-brokers/rabbitmq/.../util/FailureSimulatorTest.java`):

```java
package com.testingai.axon.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

	@Test
	void maybeThrow_shouldThrowRuntimeExceptionOccasionally() {
		int failures = 0;
		for (int i = 0; i < 500; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException e) {
				failures++;
			}
		}
		assertThat(failures).isGreaterThan(0).isLessThan(500);
	}

	@Test
	void maybeThrow_shouldIncludeContextInMessage() {
		RuntimeException caught = null;
		for (int i = 0; i < 1000 && caught == null; i++) {
			try {
				FailureSimulator.maybeThrow("myContext");
			} catch (RuntimeException e) {
				caught = e;
			}
		}
		assertThat(caught).as("Expected at least one RuntimeException in 1000 calls at 5% failure rate").isNotNull();
		assertThat(caught.getMessage()).contains("myContext");
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd cqrs-event-sourcing/axon/spring-demo && mvn test -Dtest=FailureSimulatorTest`
Expected: COMPILATION FAILURE — `FailureSimulator` does not exist yet.

- [ ] **Step 3: Implement `FailureSimulator`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/util/FailureSimulator.java`:

```java
package com.testingai.axon.util;

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

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=FailureSimulatorTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/util/FailureSimulator.java \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/util/FailureSimulatorTest.java
git commit -m "feat(axon): add FailureSimulator utility"
```

---

### Task 4: Domain events

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderCreatedEvent.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderLineAddedEvent.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderConfirmedEvent.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderCancelledEvent.java`

**Interfaces:**
- Produces: four event records, consumed by `OrderAggregate` (Task 5) and `OrderProjection` (Task 7).

These are plain data-carrying records with no behavior of their own to unit test — they're verified indirectly through `OrderAggregateTest` (Task 5) and `OrderProjectionTest` (Task 7). No dedicated test step for this task.

- [ ] **Step 1: Create the event records**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderCreatedEvent.java`:

```java
package com.testingai.axon.event;

public record OrderCreatedEvent(String orderId, String customerId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderLineAddedEvent.java`:

```java
package com.testingai.axon.event;

import java.math.BigDecimal;

public record OrderLineAddedEvent(String orderId, String productId, int quantity, BigDecimal price) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderConfirmedEvent.java`:

```java
package com.testingai.axon.event;

public record OrderConfirmedEvent(String orderId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/OrderCancelledEvent.java`:

```java
package com.testingai.axon.event;

public record OrderCancelledEvent(String orderId) {
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/event/
git commit -m "feat(axon): add Order domain events"
```

---

### Task 5: `OrderAggregate` — command handling and event sourcing

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/CreateOrderCommand.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/AddOrderLineCommand.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/ConfirmOrderCommand.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/CancelOrderCommand.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/OrderAggregate.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/command/OrderAggregateTest.java`

**Interfaces:**
- Consumes: `com.testingai.axon.event.{OrderCreatedEvent,OrderLineAddedEvent,OrderConfirmedEvent,OrderCancelledEvent}` (Task 4), `com.testingai.axon.util.FailureSimulator.maybeThrow(String)` (Task 3).
- Produces: `OrderAggregate` annotated `@Aggregate(snapshotTriggerDefinition = "orderSnapshotTriggerDefinition")` (the bean is created in Task 6 — the annotation reference is added now but the bean doesn't need to exist for `AggregateTestFixture`-based unit tests, since the fixture builds its own in-memory repository and ignores Spring bean wiring). Command records `CreateOrderCommand(String orderId, String customerId)`, `AddOrderLineCommand(String orderId, String productId, int quantity, BigDecimal price)`, `ConfirmOrderCommand(String orderId)`, `CancelOrderCommand(String orderId)` — consumed by `DemoController` in Task 9.

- [ ] **Step 1: Create the command records**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/CreateOrderCommand.java`:

```java
package com.testingai.axon.command;

public record CreateOrderCommand(String orderId, String customerId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/AddOrderLineCommand.java`:

```java
package com.testingai.axon.command;

import java.math.BigDecimal;

public record AddOrderLineCommand(String orderId, String productId, int quantity, BigDecimal price) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/ConfirmOrderCommand.java`:

```java
package com.testingai.axon.command;

public record ConfirmOrderCommand(String orderId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/CancelOrderCommand.java`:

```java
package com.testingai.axon.command;

public record CancelOrderCommand(String orderId) {
}
```

- [ ] **Step 2: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/command/OrderAggregateTest.java`:

```java
package com.testingai.axon.command;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import com.testingai.axon.util.FailureSimulator;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class OrderAggregateTest {

	private static final String ORDER_ID = "order-1";
	private static final String CUSTOMER_ID = "customer-1";

	private FixtureConfiguration<OrderAggregate> fixture;

	@BeforeEach
	void setUp() {
		fixture = new AggregateTestFixture<>(OrderAggregate.class);
	}

	@Test
	void create_shouldEmitOrderCreatedEvent() {
		fixture.givenNoPriorActivity().when(new CreateOrderCommand(ORDER_ID, CUSTOMER_ID))
				.expectEvents(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
	}

	@Test
	void addLine_shouldEmitOrderLineAddedEvent() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID))
				.when(new AddOrderLineCommand(ORDER_ID, "product-1", 2, BigDecimal.TEN))
				.expectEvents(new OrderLineAddedEvent(ORDER_ID, "product-1", 2, BigDecimal.TEN));
	}

	@Test
	void confirm_shouldEmitOrderConfirmedEvent() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new ConfirmOrderCommand(ORDER_ID))
					.expectEvents(new OrderConfirmedEvent(ORDER_ID));
		}
	}

	@Test
	void confirm_whenAlreadyConfirmed_shouldRejectAndEmitNoEvents() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID), new OrderConfirmedEvent(ORDER_ID))
					.when(new ConfirmOrderCommand(ORDER_ID)).expectException(IllegalStateException.class)
					.expectNoEvents();
		}
	}

	@Test
	void confirm_whenSimulatedFailure_shouldRejectAndEmitNoEvents() {
		try (MockedStatic<FailureSimulator> mocked = mockStatic(FailureSimulator.class)) {
			mocked.when(() -> FailureSimulator.maybeThrow(anyString()))
					.thenThrow(new RuntimeException("Simulated 5% failure in confirm-order"));

			fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new ConfirmOrderCommand(ORDER_ID))
					.expectException(RuntimeException.class).expectNoEvents();
		}
	}

	@Test
	void cancel_shouldEmitOrderCancelledEvent() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID)).when(new CancelOrderCommand(ORDER_ID))
				.expectEvents(new OrderCancelledEvent(ORDER_ID));
	}

	@Test
	void cancel_whenAlreadyConfirmed_shouldRejectAndEmitNoEvents() {
		fixture.given(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID), new OrderConfirmedEvent(ORDER_ID))
				.when(new CancelOrderCommand(ORDER_ID)).expectException(IllegalStateException.class)
				.expectNoEvents();
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn test -Dtest=OrderAggregateTest`
Expected: COMPILATION FAILURE — `OrderAggregate` does not exist yet.

- [ ] **Step 4: Implement `OrderAggregate`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/OrderAggregate.java`:

```java
package com.testingai.axon.command;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import com.testingai.axon.util.FailureSimulator;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate(snapshotTriggerDefinition = "orderSnapshotTriggerDefinition")
public class OrderAggregate {

	@AggregateIdentifier
	private String orderId;
	private boolean confirmed;

	@CommandHandler
	public OrderAggregate(CreateOrderCommand command) {
		apply(new OrderCreatedEvent(command.orderId(), command.customerId()));
	}

	@CommandHandler
	public void handle(AddOrderLineCommand command) {
		apply(new OrderLineAddedEvent(command.orderId(), command.productId(), command.quantity(), command.price()));
	}

	@CommandHandler
	public void handle(ConfirmOrderCommand command) {
		if (confirmed) {
			throw new IllegalStateException("Order " + command.orderId() + " is already confirmed");
		}
		FailureSimulator.maybeThrow("confirm-order");
		apply(new OrderConfirmedEvent(command.orderId()));
	}

	@CommandHandler
	public void handle(CancelOrderCommand command) {
		if (confirmed) {
			throw new IllegalStateException("Cannot cancel order " + command.orderId() + " after it is confirmed");
		}
		apply(new OrderCancelledEvent(command.orderId()));
	}

	@EventSourcingHandler
	public void on(OrderCreatedEvent event) {
		this.orderId = event.orderId();
	}

	@EventSourcingHandler
	public void on(OrderConfirmedEvent event) {
		this.confirmed = true;
	}

	protected OrderAggregate() {
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=OrderAggregateTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/command/ \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/command/
git commit -m "feat(axon): add OrderAggregate command handling and event sourcing"
```

---

### Task 6: Snapshot configuration

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/config/AxonConfig.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/config/AxonConfigTest.java`

**Interfaces:**
- Consumes: `org.axonframework.eventsourcing.Snapshotter` (provided by Axon's autoconfiguration at runtime; mocked in the test).
- Produces: Spring bean `orderSnapshotTriggerDefinition` of type `SnapshotTriggerDefinition`, referenced by name from `OrderAggregate`'s `@Aggregate(snapshotTriggerDefinition = "orderSnapshotTriggerDefinition")` (Task 5).

This test verifies the **wiring** (the bean exists, is the right type, and the aggregate annotation references it by the correct name) rather than asserting on Axon's internal `SnapshotTrigger`/`Snapshotter` callback sequence, which is not part of the framework's stable public test API. The actual "snapshot taken after 5 events" behavior is demonstrated end-to-end in Task 14's manual walkthrough via the Axon Server dashboard.

- [ ] **Step 1: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/config/AxonConfigTest.java`:

```java
package com.testingai.axon.config;

import com.testingai.axon.command.OrderAggregate;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AxonConfigTest {

	@Mock
	private Snapshotter snapshotter;

	@Test
	void orderSnapshotTriggerDefinition_shouldReturnEventCountTrigger() {
		AxonConfig config = new AxonConfig();

		SnapshotTriggerDefinition triggerDefinition = config.orderSnapshotTriggerDefinition(snapshotter);

		assertThat(triggerDefinition).isInstanceOf(EventCountSnapshotTriggerDefinition.class);
	}

	@Test
	void orderAggregate_shouldReferenceSnapshotTriggerDefinitionBeanByName() {
		Aggregate annotation = OrderAggregate.class.getAnnotation(Aggregate.class);

		assertThat(annotation.snapshotTriggerDefinition()).isEqualTo("orderSnapshotTriggerDefinition");
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=AxonConfigTest`
Expected: COMPILATION FAILURE — `AxonConfig` does not exist yet.

- [ ] **Step 3: Implement `AxonConfig`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/config/AxonConfig.java`:

```java
package com.testingai.axon.config;

import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {

	private static final int SNAPSHOT_EVENT_COUNT_THRESHOLD = 5;

	@Bean
	public SnapshotTriggerDefinition orderSnapshotTriggerDefinition(Snapshotter snapshotter) {
		return new EventCountSnapshotTriggerDefinition(snapshotter, SNAPSHOT_EVENT_COUNT_THRESHOLD);
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=AxonConfigTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/config/ \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/config/
git commit -m "feat(axon): configure event-count snapshot trigger for OrderAggregate"
```

---

### Task 7: Query side — `OrderProjection`

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/OrderSummary.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/FindOrderQuery.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/FindAllOrdersQuery.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/OrderProjection.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/query/OrderProjectionTest.java`

**Interfaces:**
- Consumes: `com.testingai.axon.event.{OrderCreatedEvent,OrderLineAddedEvent,OrderConfirmedEvent,OrderCancelledEvent}` (Task 4).
- Produces: `OrderSummary(String orderId, String customerId, int lineCount, String status)`; `FindOrderQuery(String orderId)`; `FindAllOrdersQuery()`; `OrderProjection` — a `@ProcessingGroup("order-projection")` Spring component with `@EventHandler` methods (`on(OrderCreatedEvent)`, `on(OrderLineAddedEvent)`, `on(OrderConfirmedEvent)`, `on(OrderCancelledEvent)`), a `@ResetHandler` clearing the read model, and `@QueryHandler` methods `handle(FindOrderQuery): OrderSummary` (nullable) and `handle(FindAllOrdersQuery): List<OrderSummary>`. Consumed by `DemoController` in Task 9 via `QueryGateway`, and by `ReplayService` in Task 8 (which targets the `"order-projection"` processing group by name).

- [ ] **Step 1: Create the read-model record and query records**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/OrderSummary.java`:

```java
package com.testingai.axon.query;

public record OrderSummary(String orderId, String customerId, int lineCount, String status) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/FindOrderQuery.java`:

```java
package com.testingai.axon.query;

public record FindOrderQuery(String orderId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/FindAllOrdersQuery.java`:

```java
package com.testingai.axon.query;

public record FindAllOrdersQuery() {
}
```

- [ ] **Step 2: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/query/OrderProjectionTest.java` — calls the event/query handler methods directly (no Spring context needed, since `@EventHandler`/`@QueryHandler` methods are plain public methods Axon discovers via reflection at runtime):

```java
package com.testingai.axon.query;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProjectionTest {

	private static final String ORDER_ID = "order-1";
	private static final String CUSTOMER_ID = "customer-1";

	private final OrderProjection projection = new OrderProjection();

	@Test
	void onOrderCreated_shouldAddSummaryWithCreatedStatus() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary).isEqualTo(new OrderSummary(ORDER_ID, CUSTOMER_ID, 0, "CREATED"));
	}

	@Test
	void onOrderLineAdded_shouldIncrementLineCount() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderLineAddedEvent(ORDER_ID, "product-1", 2, BigDecimal.TEN));
		projection.on(new OrderLineAddedEvent(ORDER_ID, "product-2", 1, BigDecimal.ONE));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.lineCount()).isEqualTo(2);
	}

	@Test
	void onOrderConfirmed_shouldUpdateStatusToConfirmed() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderConfirmedEvent(ORDER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.status()).isEqualTo("CONFIRMED");
	}

	@Test
	void onOrderCancelled_shouldUpdateStatusToCancelled() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));
		projection.on(new OrderCancelledEvent(ORDER_ID));

		OrderSummary summary = projection.handle(new FindOrderQuery(ORDER_ID));

		assertThat(summary.status()).isEqualTo("CANCELLED");
	}

	@Test
	void handleFindOrderQuery_shouldReturnNullWhenOrderUnknown() {
		assertThat(projection.handle(new FindOrderQuery("missing"))).isNull();
	}

	@Test
	void handleFindAllOrdersQuery_shouldReturnAllKnownOrders() {
		projection.on(new OrderCreatedEvent("order-1", CUSTOMER_ID));
		projection.on(new OrderCreatedEvent("order-2", CUSTOMER_ID));

		List<OrderSummary> summaries = projection.handle(new FindAllOrdersQuery());

		assertThat(summaries).hasSize(2).extracting(OrderSummary::orderId).containsExactlyInAnyOrder("order-1",
				"order-2");
	}

	@Test
	void onReset_shouldClearAllSummaries() {
		projection.on(new OrderCreatedEvent(ORDER_ID, CUSTOMER_ID));

		projection.onReset();

		assertThat(projection.handle(new FindAllOrdersQuery())).isEmpty();
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn test -Dtest=OrderProjectionTest`
Expected: COMPILATION FAILURE — `OrderProjection` does not exist yet.

- [ ] **Step 4: Implement `OrderProjection`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/OrderProjection.java`:

```java
package com.testingai.axon.query;

import com.testingai.axon.event.OrderCancelledEvent;
import com.testingai.axon.event.OrderConfirmedEvent;
import com.testingai.axon.event.OrderCreatedEvent;
import com.testingai.axon.event.OrderLineAddedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

	private final Map<String, OrderSummary> orders = new ConcurrentHashMap<>();

	@EventHandler
	public void on(OrderCreatedEvent event) {
		orders.put(event.orderId(), new OrderSummary(event.orderId(), event.customerId(), 0, "CREATED"));
	}

	@EventHandler
	public void on(OrderLineAddedEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount() + 1, summary.status()));
	}

	@EventHandler
	public void on(OrderConfirmedEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount(), "CONFIRMED"));
	}

	@EventHandler
	public void on(OrderCancelledEvent event) {
		orders.computeIfPresent(event.orderId(), (orderId, summary) -> new OrderSummary(summary.orderId(),
				summary.customerId(), summary.lineCount(), "CANCELLED"));
	}

	@ResetHandler
	public void onReset() {
		orders.clear();
	}

	@QueryHandler
	public OrderSummary handle(FindOrderQuery query) {
		return orders.get(query.orderId());
	}

	@QueryHandler
	public List<OrderSummary> handle(FindAllOrdersQuery query) {
		return List.copyOf(orders.values());
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=OrderProjectionTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/query/ \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/query/
git commit -m "feat(axon): add OrderProjection query-side read model"
```

---

### Task 8: Replay service

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/replay/ReplayService.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/replay/ReplayServiceTest.java`

**Interfaces:**
- Consumes: `org.axonframework.config.EventProcessingConfiguration` (Axon-provided Spring bean), targets the `"order-projection"` processing group (Task 7).
- Produces: `ReplayService.replayOrderProjection()` — consumed by `DemoController` in Task 9.

- [ ] **Step 1: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/replay/ReplayServiceTest.java`:

```java
package com.testingai.axon.replay;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

	@InjectMocks
	private ReplayService replayService;

	@Mock
	private EventProcessingConfiguration eventProcessingConfiguration;

	@Mock
	private TrackingEventProcessor trackingEventProcessor;

	@Test
	void replayOrderProjection_shouldShutDownResetAndRestartTheProcessor() {
		when(eventProcessingConfiguration.eventProcessor("order-projection", TrackingEventProcessor.class))
				.thenReturn(Optional.of(trackingEventProcessor));

		replayService.replayOrderProjection();

		verify(trackingEventProcessor).shutDown();
		verify(trackingEventProcessor).resetTokens();
		verify(trackingEventProcessor).start();
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=ReplayServiceTest`
Expected: COMPILATION FAILURE — `ReplayService` does not exist yet.

- [ ] **Step 3: Implement `ReplayService`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/replay/ReplayService.java`:

```java
package com.testingai.axon.replay;

import lombok.RequiredArgsConstructor;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayService {

	private static final String PROCESSING_GROUP = "order-projection";

	private final EventProcessingConfiguration eventProcessingConfiguration;

	public void replayOrderProjection() {
		eventProcessingConfiguration.eventProcessor(PROCESSING_GROUP, TrackingEventProcessor.class)
				.ifPresent(processor -> {
					processor.shutDown();
					processor.resetTokens();
					processor.start();
				});
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=ReplayServiceTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/replay/ \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/replay/
git commit -m "feat(axon): add ReplayService to rebuild the order projection from history"
```

---

### Task 9: `DemoController` and error handling

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/CreateOrderRequest.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/AddOrderLineRequest.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/DemoController.java`
- Create: `cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/DemoExceptionHandler.java`
- Test: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `CommandGateway.sendAndWait(Object)`, `QueryGateway.query(Object, Class)`/`query(Object, ResponseType)` (Axon-provided beans), `com.testingai.axon.replay.ReplayService.replayOrderProjection()` (Task 8), the four command records (Task 5), `FindOrderQuery`/`FindAllOrdersQuery`/`OrderSummary` (Task 7).
- Produces: REST API at `/demo/orders/*` exactly as specified in the design spec.

- [ ] **Step 1: Create the request DTOs**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/CreateOrderRequest.java`:

```java
package com.testingai.axon.controller;

public record CreateOrderRequest(String customerId) {
}
```

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/AddOrderLineRequest.java`:

```java
package com.testingai.axon.controller;

import java.math.BigDecimal;

public record AddOrderLineRequest(String productId, int quantity, BigDecimal price) {
}
```

- [ ] **Step 2: Write the failing test**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/controller/DemoControllerTest.java`:

```java
package com.testingai.axon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.axon.command.AddOrderLineCommand;
import com.testingai.axon.command.CancelOrderCommand;
import com.testingai.axon.command.ConfirmOrderCommand;
import com.testingai.axon.command.CreateOrderCommand;
import com.testingai.axon.query.FindAllOrdersQuery;
import com.testingai.axon.query.FindOrderQuery;
import com.testingai.axon.query.OrderSummary;
import com.testingai.axon.replay.ReplayService;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CommandGateway commandGateway;
	@MockitoBean
	private QueryGateway queryGateway;
	@MockitoBean
	private ReplayService replayService;

	@Test
	void createOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateOrderRequest("customer-1"))))
				.andExpect(status().isOk());

		verify(commandGateway).sendAndWait(any(CreateOrderCommand.class));
	}

	@Test
	void addLine_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/lines").contentType("application/json")
				.content(objectMapper.writeValueAsString(new AddOrderLineRequest("product-1", 2, BigDecimal.TEN))))
				.andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new AddOrderLineCommand("order-1", "product-1", 2, BigDecimal.TEN));
	}

	@Test
	void confirmOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/confirm")).andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new ConfirmOrderCommand("order-1"));
	}

	@Test
	void confirmOrder_whenAlreadyConfirmed_shouldReturn409() throws Exception {
		when(commandGateway.sendAndWait(new ConfirmOrderCommand("order-1")))
				.thenThrow(new CommandExecutionException("failed", new IllegalStateException("already confirmed")));

		mockMvc.perform(post("/demo/orders/order-1/confirm")).andExpect(status().isConflict());
	}

	@Test
	void cancelOrder_shouldReturn200AndDispatchCommand() throws Exception {
		mockMvc.perform(post("/demo/orders/order-1/cancel")).andExpect(status().isOk());

		verify(commandGateway).sendAndWait(new CancelOrderCommand("order-1"));
	}

	@Test
	void getOrder_shouldReturn200WhenFound() throws Exception {
		OrderSummary summary = new OrderSummary("order-1", "customer-1", 1, "CREATED");
		when(queryGateway.query(eq(new FindOrderQuery("order-1")), eq(OrderSummary.class)))
				.thenReturn(CompletableFuture.completedFuture(summary));

		mockMvc.perform(get("/demo/orders/order-1")).andExpect(status().isOk());
	}

	@Test
	void getOrder_shouldReturn404WhenMissing() throws Exception {
		when(queryGateway.query(eq(new FindOrderQuery("missing")), eq(OrderSummary.class)))
				.thenReturn(CompletableFuture.completedFuture(null));

		mockMvc.perform(get("/demo/orders/missing")).andExpect(status().isNotFound());
	}

	@Test
	void getAllOrders_shouldReturn200() throws Exception {
		when(queryGateway.query(eq(new FindAllOrdersQuery()), eq(ResponseTypes.multipleInstancesOf(OrderSummary.class))))
				.thenReturn(CompletableFuture.completedFuture(List.of()));

		mockMvc.perform(get("/demo/orders")).andExpect(status().isOk());
	}

	@Test
	void replay_shouldReturn202AndDelegate() throws Exception {
		mockMvc.perform(post("/demo/orders/replay")).andExpect(status().isAccepted());

		verify(replayService).replayOrderProjection();
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: COMPILATION FAILURE — `DemoController` does not exist yet.

- [ ] **Step 4: Implement `DemoController`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/DemoController.java`:

```java
package com.testingai.axon.controller;

import com.testingai.axon.command.AddOrderLineCommand;
import com.testingai.axon.command.CancelOrderCommand;
import com.testingai.axon.command.ConfirmOrderCommand;
import com.testingai.axon.command.CreateOrderCommand;
import com.testingai.axon.query.FindAllOrdersQuery;
import com.testingai.axon.query.FindOrderQuery;
import com.testingai.axon.query.OrderSummary;
import com.testingai.axon.replay.ReplayService;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo/orders")
@RequiredArgsConstructor
public class DemoController {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;
	private final ReplayService replayService;

	@PostMapping
	public String createOrder(@RequestBody CreateOrderRequest request) {
		String orderId = UUID.randomUUID().toString();
		commandGateway.sendAndWait(new CreateOrderCommand(orderId, request.customerId()));
		return orderId;
	}

	@PostMapping("/{orderId}/lines")
	public void addLine(@PathVariable String orderId, @RequestBody AddOrderLineRequest request) {
		commandGateway.sendAndWait(
				new AddOrderLineCommand(orderId, request.productId(), request.quantity(), request.price()));
	}

	@PostMapping("/{orderId}/confirm")
	public void confirmOrder(@PathVariable String orderId) {
		commandGateway.sendAndWait(new ConfirmOrderCommand(orderId));
	}

	@PostMapping("/{orderId}/cancel")
	public void cancelOrder(@PathVariable String orderId) {
		commandGateway.sendAndWait(new CancelOrderCommand(orderId));
	}

	@GetMapping("/{orderId}")
	public OrderSummary getOrder(@PathVariable String orderId) {
		OrderSummary summary = queryGateway.query(new FindOrderQuery(orderId), OrderSummary.class).join();
		if (summary == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found");
		}
		return summary;
	}

	@GetMapping
	public List<OrderSummary> getAllOrders() {
		return queryGateway.query(new FindAllOrdersQuery(), ResponseTypes.multipleInstancesOf(OrderSummary.class))
				.join();
	}

	@PostMapping("/replay")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void replay() {
		replayService.replayOrderProjection();
	}
}
```

- [ ] **Step 5: Implement `DemoExceptionHandler`**

`cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/DemoExceptionHandler.java`:

```java
package com.testingai.axon.controller;

import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(CommandExecutionException.class)
	public ResponseEntity<String> handleCommandExecutionException(CommandExecutionException exception) {
		Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
		HttpStatus status = cause instanceof IllegalStateException ? HttpStatus.CONFLICT
				: HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(cause.getMessage());
	}
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 7: Run the full test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all tests across `util`, `command`, `config`, `query`, `replay`, `controller` packages passing.

- [ ] **Step 8: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/main/java/com/testingai/axon/controller/ \
  cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/controller/
git commit -m "feat(axon): add DemoController REST API and command-failure exception mapping"
```

---

### Task 10: Gatling performance test

**Files:**
- Create: `cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running `axon-demo` app (Task 9's REST API) and a running Axon Server (Task 2). Not run as part of `mvn test` (excluded via `**/performance/**` in the parent POM's surefire config, same as every other module).

- [ ] **Step 1: Write the simulation**

`cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/performance/DemoSimulation.java`:

```java
package com.testingai.axon.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8085")
			.acceptHeader("application/json").contentTypeHeader("application/json");

	private final ScenarioBuilder orderLifecycleScenario = scenario("Order Lifecycle")
			.exec(exec(http("Create Order").post("/demo/orders").body(StringBody("{\"customerId\":\"load-test\"}"))
					.check(status().is(200))))
			.exec(exec(http("Get All Orders").get("/demo/orders").check(status().is(200))));

	{
		setUp(orderLifecycleScenario.injectOpen(atOnceUsers(10))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn test-compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add cqrs-event-sourcing/axon/spring-demo/src/test/java/com/testingai/axon/performance/
git commit -m "feat(axon): add Gatling load test for the order lifecycle"
```

---

### Task 11: Cross-cutting fixes — pre-commit hook and `CLAUDE.md`

**Files:**
- Modify: `.githooks/pre-commit`
- Modify: `CLAUDE.md`

**Interfaces:** None — these are repo-wide configuration/doc changes with no code interface.

- [ ] **Step 1: Extend the pre-commit hook**

Modify `.githooks/pre-commit`. Current content (relevant lines):

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL)/.*\.java$' || true)

if [ -z "$STAGED_JAVA" ]; then
    exit 0
fi

if echo "$STAGED_JAVA" | grep -q '^message-brokers/'; then
    echo "[pre-commit] Applying Spotless formatting to staged message-brokers Java files..."
    (cd "$ROOT/message-brokers" && mvn spotless:apply --quiet)
fi

if echo "$STAGED_JAVA" | grep -q '^noSQL/'; then
    echo "[pre-commit] Applying Spotless formatting to staged noSQL Java files..."
    (cd "$ROOT/noSQL" && mvn spotless:apply --quiet)
fi
```

Replace with:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing)/.*\.java$' || true)

if [ -z "$STAGED_JAVA" ]; then
    exit 0
fi

if echo "$STAGED_JAVA" | grep -q '^message-brokers/'; then
    echo "[pre-commit] Applying Spotless formatting to staged message-brokers Java files..."
    (cd "$ROOT/message-brokers" && mvn spotless:apply --quiet)
fi

if echo "$STAGED_JAVA" | grep -q '^noSQL/'; then
    echo "[pre-commit] Applying Spotless formatting to staged noSQL Java files..."
    (cd "$ROOT/noSQL" && mvn spotless:apply --quiet)
fi

if echo "$STAGED_JAVA" | grep -q '^cqrs-event-sourcing/'; then
    echo "[pre-commit] Applying Spotless formatting to staged cqrs-event-sourcing Java files..."
    (cd "$ROOT/cqrs-event-sourcing" && mvn spotless:apply --quiet)
fi
```

Use the Edit tool with `old_string` set to the two-line `STAGED_JAVA=...` block plus the `noSQL` `if` block, `new_string` as shown above (the `message-brokers` block in between is unchanged and should stay in place).

- [ ] **Step 2: Verify the hook is syntactically valid**

Run: `bash -n .githooks/pre-commit`
Expected: no output (exit code 0).

- [ ] **Step 3: Update `CLAUDE.md`**

Add a new subsection after the existing "NoSQL database demos" command block (search for `### NoSQL database demos`):

```markdown
### CQRS/Event Sourcing demos (Axon Framework — run from the module root)

```bash
cd cqrs-event-sourcing/axon/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app and Axon Server running first
```
```

Add a new row to the "Repository layout" table (after the `noSQL/<database>/spring-demo/` row):

```markdown
| `cqrs-event-sourcing/<framework>/spring-demo/` | CQRS/event-sourcing framework demo apps, same conventions as `message-brokers/` (currently: Axon Framework) |
```

Add a new line to the "Infrastructure" code block, after the `noSQL` lines and before the closing fence:

```bash
docker compose -f cqrs-event-sourcing/axon/docker/docker-compose.yml up -d
```

- [ ] **Step 4: Commit**

```bash
git add .githooks/pre-commit CLAUDE.md
git commit -m "docs(axon): wire cqrs-event-sourcing into pre-commit hook and CLAUDE.md"
```

---

### Task 12: Module READMEs

**Files:**
- Create: `cqrs-event-sourcing/README.md`
- Create: `cqrs-event-sourcing/axon/README.md`

**Interfaces:** None.

- [ ] **Step 1: Write the category index README**

`cqrs-event-sourcing/README.md` (mirrors `noSQL/README.md`):

```markdown
# CQRS / Event Sourcing — Demos

This directory contains runnable demos for CQRS/event-sourcing frameworks, structured the same way as `../message-brokers/` and `../noSQL/`: one infrastructure component and one Spring Boot demo app per framework.

| Framework | Infrastructure | Best fit |
|---|---|---|
| [Axon Framework](axon/) | Axon Server (single node) | Commands, event-sourced aggregates, decoupled query models, event replay, snapshotting |

More CQRS/event-sourcing frameworks may be added here over time, at which point this README will grow into a comparison guide like `../message-brokers/README.md`.
```

- [ ] **Step 2: Write the `axon` module README**

`cqrs-event-sourcing/axon/README.md`:

```markdown
# Axon Framework Demo

A Spring Boot app demonstrating CQRS/event-sourcing fundamentals with [Axon Framework](https://www.axoniq.io/products/axon-framework) and [Axon Server](https://www.axoniq.io/products/axon-server) as the event store and command/query router.

## Prerequisites

- Java 21
- Maven
- Docker (for Axon Server)

## Start Axon Server

```bash
docker compose -f docker/docker-compose.yml up -d
```

Wait ~15 seconds, then verify it's healthy:

```bash
curl -f http://localhost:8024/actuator/health
```

Open the dashboard at [http://localhost:8024](http://localhost:8024) to browse aggregates, events, and snapshots as you exercise the API below.

## Run the app

```bash
cd spring-demo
mvn spring-boot:run
```

The app starts on port `8085` and connects to Axon Server at `localhost:8124`.

Swagger UI: [http://localhost:8085/swagger-ui/index.html](http://localhost:8085/swagger-ui/index.html)

## Architecture

```
REST request
    │
    ▼
DemoController ──CommandGateway──▶ OrderAggregate (event-sourced)
                                        │ apply(event)
                                        ▼
                                  Axon Server (event store)
                                        │ TrackingEventProcessor
                                        ▼
                                  OrderProjection (read model)
    ▲
    │ QueryGateway
DemoController
```

## Patterns demonstrated

| Pattern | Where | What it shows |
|---|---|---|
| Command handling + event sourcing | `command/OrderAggregate.java` | Commands are validated and turned into events; aggregate state is rebuilt purely by replaying those events |
| CQRS query model | `query/OrderProjection.java` | A separate, denormalized read model updated asynchronously from the same events |
| Replay | `replay/ReplayService.java` | Resetting the tracking processor clears and rebuilds the read model from Axon Server's full event history |
| Snapshotting | `config/AxonConfig.java` | After 5 events on one `OrderAggregate`, Axon persists a snapshot, bounding future replay cost |

## Try it

```bash
# Create an order
ORDER_ID=$(curl -s -X POST http://localhost:8085/demo/orders \
  -H "Content-Type: application/json" -d '{"customerId":"customer-1"}')

# Add a few order lines (5+ triggers a snapshot — watch the Axon Server dashboard)
for i in 1 2 3 4 5 6; do
  curl -X POST "http://localhost:8085/demo/orders/$ORDER_ID/lines" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"product-$i\",\"quantity\":1,\"price\":9.99}"
done

# Confirm it
curl -X POST "http://localhost:8085/demo/orders/$ORDER_ID/confirm"

# Query the read model
curl "http://localhost:8085/demo/orders/$ORDER_ID"
curl "http://localhost:8085/demo/orders"

# Rebuild the read model from the event store
curl -X POST "http://localhost:8085/demo/orders/replay"
```

Cancelling a confirmed order is rejected:

```bash
curl -i -X POST "http://localhost:8085/demo/orders/$ORDER_ID/cancel"
# HTTP/1.1 409 — "Cannot cancel order ... after it is confirmed"
```

## Performance tests

```bash
cd spring-demo
mvn gatling:test
```

Requires the app and Axon Server running first.

## Stop Axon Server

```bash
docker compose -f docker/docker-compose.yml down
```

Add `-v` to also remove the event store volumes.
```

- [ ] **Step 3: Commit**

```bash
git add cqrs-event-sourcing/README.md cqrs-event-sourcing/axon/README.md
git commit -m "docs(axon): add cqrs-event-sourcing and axon module READMEs"
```

---

### Task 13: End-to-end manual verification

**Files:** None — verification only.

- [ ] **Step 1: Confirm Axon Server is running**

Run: `docker compose -f cqrs-event-sourcing/axon/docker/docker-compose.yml ps`
Expected: `axonserver` service `Up`.

- [ ] **Step 2: Start the app**

Run: `cd cqrs-event-sourcing/axon/spring-demo && mvn spring-boot:run` (in a separate terminal/background process)
Expected: log line confirming connection to Axon Server (`Connected to AxonServer node ...`) and `Started AxonDemoApplication`.

- [ ] **Step 3: Walk through the full lifecycle**

Run the `curl` sequence from `cqrs-event-sourcing/axon/README.md`'s "Try it" section: create an order, add 6 lines, confirm, query single + list, replay, attempt cancel-after-confirm.

Expected:
- Create returns a 200 with a UUID body.
- Each "add line" call returns 200.
- `GET /demo/orders/$ORDER_ID` shows `lineCount: 6`, `status: "CREATED"` before confirm, `"CONFIRMED"` after.
- `GET /demo/orders` includes the created order.
- `POST /demo/orders/$ORDER_ID/replay` returns 202; a subsequent `GET /demo/orders/$ORDER_ID` still shows the same state (rebuilt from history, not lost).
- Cancel-after-confirm returns 409 with a body containing "after it is confirmed".

- [ ] **Step 4: Confirm the snapshot in the Axon Server dashboard**

Open `http://localhost:8024`, navigate to the aggregate's event stream for the created `orderId`. Expected: a snapshot event appears after the 5th domain event, alongside the regular `OrderCreatedEvent`/`OrderLineAddedEvent`/... entries.

- [ ] **Step 5: Stop the app and Axon Server**

Stop the `mvn spring-boot:run` process, then:

```bash
docker compose -f cqrs-event-sourcing/axon/docker/docker-compose.yml down
```

No commit for this task — it's verification only.

---

## Self-Review Notes

- **Spec coverage:** Repository structure (Task 1), Axon Server topology (Task 2), all four demo patterns — command/event-sourcing (Task 5), CQRS query model (Task 7), replay (Task 8), snapshotting (Task 6) — REST API (Task 9), error handling (Task 9), Spring Boot configuration (Tasks 1, 6), testing conventions (Tasks 3, 5, 6, 7, 8, 9, 10), README (Task 12), cross-cutting fixes (Task 11) are all covered by a task.
- **Placeholder scan:** No TBD/TODO markers; every code step contains complete, compilable code.
- **Type consistency:** `OrderSummary(String orderId, String customerId, int lineCount, String status)` is used identically across Tasks 7 and 9. `ReplayService.replayOrderProjection()` name matches between Tasks 8 and 9. `"order-projection"` processing-group name is consistent across `application.yml` (Task 1), `OrderProjection`'s `@ProcessingGroup` (Task 7), and `ReplayService`'s `PROCESSING_GROUP` constant (Task 8).
