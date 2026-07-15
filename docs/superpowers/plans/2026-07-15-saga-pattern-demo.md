# Saga Pattern Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `distributed-transactions/` Maven reactor containing a single Spring Boot demo app — `saga-demo` — that demonstrates the Saga pattern via both choreography (event-driven, no coordinator) and orchestration (a central orchestrator), over an e-commerce checkout scenario (reserve inventory → charge payment → arrange shipping) with deterministic, on-demand compensation.

**Architecture:** No external infrastructure — choreography uses in-process `ApplicationEventPublisher`/`@EventListener` (synchronous), not a broker. Two independent implementations of the same three-step business flow live in `com.testingai.saga.choreography` and `com.testingai.saga.orchestration`, sharing only the `com.testingai.saga.domain` package (`SagaStep`, `SagaStatus`, `OrderLine`, `CheckoutRequest`). A single `DemoController` exposes both flows. `failAt` on the request deterministically forces a given step to fail, triggering compensation.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Lombok, springdoc-openapi, JUnit 5 + AssertJ + Mockito, Gatling.

## Global Constraints

- Java 21, Spring Boot 3.4.4 (matches every other module in this repo).
- No `util/FailureSimulator` — failures are deterministic via `CheckoutRequest.failAt`, not randomized (per the approved design spec).
- No docker-compose / external infrastructure — everything is in-memory, in-process.
- Prefer records, sealed interfaces, pattern matching (`instanceof`, `switch`), switch expressions, text blocks, `SequencedCollection` over pre-Java-21 idioms, on any line this plan adds.
- No explicit `.toString()` on values passed to SLF4J `{}` placeholders or string concatenation.
- All instance fields assigned once must be `private final`, except fields assigned in `@PostConstruct`/`ApplicationRunner.run()`/`@PreDestroy`, which must still be `private`.
- Formatting is enforced by Spotless (`spotless:apply`, wired into `distributed-transactions/pom.xml`'s git hook) — do not hand-format; let Spotless reformat on commit.
- Choreography's `ApplicationEventPublisher.publishEvent` is used synchronously (default Spring behavior, no `@Async`) — deliberate, not an oversight; see the design spec's choreography section for why.
- App port is `8089` (next free slot after `template-engines/freemarker`'s `8088`).

---

### Task 1: Scaffold the `distributed-transactions/` Maven reactor

**Files:**
- Create: `distributed-transactions/pom.xml`
- Create: `distributed-transactions/eclipse-formatter.xml`
- Create: `distributed-transactions/README.md`

**Interfaces:**
- Produces: Maven parent coordinates `com.testingai:distributed-transactions:1.0.0` (packaging `pom`), properties `lombok.version`, `springdoc.version`, `gatling.version`, `gatling-maven-plugin.version`, `spotless.version` — consumed by `saga/spring-demo`'s POM (Task 2).

- [ ] **Step 1: Create the parent POM**

`distributed-transactions/pom.xml`:

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
    <artifactId>distributed-transactions</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Distributed Transactions</name>
    <description>Parent POM for all distributed-transaction pattern demo modules</description>

    <modules>
        <module>saga/spring-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <springdoc.version>2.8.6</springdoc.version>
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

`distributed-transactions/eclipse-formatter.xml` — identical content to `template-engines/eclipse-formatter.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<profiles version="21">
    <profile kind="CodeFormatterProfile" name="techmix" version="21">
        <setting id="org.eclipse.jdt.core.formatter.lineSplit" value="120"/>
        <setting id="org.eclipse.jdt.core.formatter.comment.line_length" value="120"/>
        <setting id="org.eclipse.jdt.core.formatter.comment.format_javadoc_comments" value="true"/>
        <setting id="org.eclipse.jdt.core.formatter.comment.format_block_comments" value="true"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="tab"/>
        <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="4"/>
        <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="4"/>
    </profile>
</profiles>
```

- [ ] **Step 3: Write a placeholder top-level README (finalized in Task 13)**

`distributed-transactions/README.md`:

```markdown
# Distributed Transactions — Demos

This directory contains runnable demos for distributed-transaction patterns, structured the same way as `../template-engines/`: one Spring Boot demo app per pattern, no external infrastructure required.

| Pattern | Demo | Best fit |
|---|---|---|
| [Saga](saga/) | Choreography + orchestration, e-commerce checkout | Coordinating a multi-step business transaction across services without distributed 2PC |

More distributed-transaction patterns may be added here over time.
```

- [ ] **Step 4: Commit**

```bash
git add distributed-transactions/pom.xml distributed-transactions/eclipse-formatter.xml distributed-transactions/README.md
git commit -m "feat(distributed-transactions): scaffold distributed-transactions Maven reactor"
```

---

### Task 2: Scaffold the `saga-demo` module skeleton

**Files:**
- Create: `distributed-transactions/saga/spring-demo/pom.xml`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/SagaDemoApplication.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/resources/application.yml`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/SagaDemoApplicationTest.java`

**Interfaces:**
- Produces: `com.testingai.saga.SagaDemoApplication` (Spring Boot main class), Maven coordinates `com.testingai:saga-demo`, server port `8089`.

- [ ] **Step 1: Create the module POM**

`distributed-transactions/saga/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>distributed-transactions</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>saga-demo</artifactId>
    <name>Saga Pattern Demo</name>
    <description>Learning and demonstration project for the Saga pattern (choreography and orchestration)</description>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.saga.SagaDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.saga.performance.SagaSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the Spring Boot main class**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/SagaDemoApplication.java`:

```java
package com.testingai.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SagaDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SagaDemoApplication.class, args);
	}
}
```

- [ ] **Step 3: Create `application.yml`**

`distributed-transactions/saga/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8089
```

- [ ] **Step 4: Write the application smoke test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/SagaDemoApplicationTest.java`:

```java
package com.testingai.saga;

import org.junit.jupiter.api.Test;

class SagaDemoApplicationTest {

	@Test
	void mainClassExists() {
		new SagaDemoApplication();
	}
}
```

- [ ] **Step 5: Build the reactor**

Run: `cd distributed-transactions && mvn clean package`
Expected: `BUILD SUCCESS`, with `SagaDemoApplicationTest` reported passing.

- [ ] **Step 6: Commit**

```bash
git add distributed-transactions/saga/spring-demo/pom.xml \
  distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/SagaDemoApplication.java \
  distributed-transactions/saga/spring-demo/src/main/resources/application.yml \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/SagaDemoApplicationTest.java
git commit -m "feat(saga): scaffold saga-demo module"
```

---

### Task 3: Domain model shared by both flows

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/SagaStep.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/SagaStatus.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/OrderLine.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/CheckoutRequest.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/domain/CheckoutRequestTest.java`

**Interfaces:**
- Produces: `enum SagaStep { RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING }`, `enum SagaStatus { PENDING, CONFIRMED, CANCELLED }`, `record OrderLine(String productId, int quantity, BigDecimal unitPrice)`, `record CheckoutRequest(String customerId, List<OrderLine> items, SagaStep failAt)` — consumed by every later task in both `choreography/` and `orchestration/`.

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/domain/CheckoutRequestTest.java`:

```java
package com.testingai.saga.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestTest {

	@Test
	void checkoutRequest_shouldExposeCustomerItemsAndFailAt() {
		OrderLine line = new OrderLine("p1", 2, new BigDecimal("9.99"));
		CheckoutRequest request = new CheckoutRequest("customer-1", List.of(line), SagaStep.PROCESS_PAYMENT);

		assertThat(request.customerId()).isEqualTo("customer-1");
		assertThat(request.items()).containsExactly(line);
		assertThat(request.failAt()).isEqualTo(SagaStep.PROCESS_PAYMENT);
	}

	@Test
	void checkoutRequest_shouldAllowNullFailAtForHappyPath() {
		CheckoutRequest request = new CheckoutRequest("customer-1", List.of(), null);

		assertThat(request.failAt()).isNull();
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd distributed-transactions/saga/spring-demo && mvn test -Dtest=CheckoutRequestTest`
Expected: COMPILATION FAILURE — `SagaStep`, `OrderLine`, `CheckoutRequest` do not exist yet.

- [ ] **Step 3: Implement the domain types**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/SagaStep.java`:

```java
package com.testingai.saga.domain;

public enum SagaStep {
	RESERVE_INVENTORY,
	PROCESS_PAYMENT,
	ARRANGE_SHIPPING
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/SagaStatus.java`:

```java
package com.testingai.saga.domain;

public enum SagaStatus {
	PENDING,
	CONFIRMED,
	CANCELLED
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/OrderLine.java`:

```java
package com.testingai.saga.domain;

import java.math.BigDecimal;

public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/CheckoutRequest.java`:

```java
package com.testingai.saga.domain;

import java.util.List;

public record CheckoutRequest(String customerId, List<OrderLine> items, SagaStep failAt) {
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=CheckoutRequestTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/domain/ \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/domain/
git commit -m "feat(saga): add shared domain model (SagaStep, SagaStatus, OrderLine, CheckoutRequest)"
```

---

### Task 4: Choreography events and `SagaLog`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/CheckoutRequested.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderCreated.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReserved.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReservationFailed.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentProcessed.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentFailed.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentRefunded.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/ShipmentArranged.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/ShipmentFailed.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReleased.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderConfirmed.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderCancelled.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/SagaLogEntry.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/SagaLog.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/SagaLogTest.java`

**Interfaces:**
- Consumes: `SagaStep`, `OrderLine` (Task 3).
- Produces: the 12 event records listed above (all carry `String orderId` as their first component); `SagaLogEntry(String step, SagaLogEntry.Outcome outcome, String detail, Instant timestamp)` with nested `enum Outcome { SUCCEEDED, FAILED, COMPENSATED }`; `SagaLog` with `void append(String orderId, String step, SagaLogEntry.Outcome outcome, String detail)` and `List<SagaLogEntry> timelineFor(String orderId)` — consumed by every choreography participant (Tasks 5–8) and the controller (Task 11). This task adds all 12 event records in one pass — they are plain data carriers with no independent behavior to test individually; `SagaLogTest` plus the participant tests in later tasks exercise them.

- [ ] **Step 1: Write the failing test for `SagaLog`**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/SagaLogTest.java`:

```java
package com.testingai.saga.choreography;

import org.junit.jupiter.api.Test;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.COMPENSATED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

class SagaLogTest {

	private final SagaLog sagaLog = new SagaLog();

	@Test
	void timelineFor_shouldReturnEmptyListForUnknownOrder() {
		assertThat(sagaLog.timelineFor("missing")).isEmpty();
	}

	@Test
	void append_shouldAccumulateEntriesInOrderPerOrderId() {
		sagaLog.append("order-1", "ORDER_CREATED", SUCCEEDED, null);
		sagaLog.append("order-1", "INVENTORY_RESERVED", SUCCEEDED, null);
		sagaLog.append("order-2", "ORDER_CREATED", SUCCEEDED, null);

		assertThat(sagaLog.timelineFor("order-1")).extracting(SagaLogEntry::step)
				.containsExactly("ORDER_CREATED", "INVENTORY_RESERVED");
		assertThat(sagaLog.timelineFor("order-2")).extracting(SagaLogEntry::step).containsExactly("ORDER_CREATED");
	}

	@Test
	void append_shouldRecordOutcomeAndDetail() {
		sagaLog.append("order-1", "INVENTORY_RELEASED", COMPENSATED, "payment failed upstream");

		assertThat(sagaLog.timelineFor("order-1")).singleElement().satisfies(entry -> {
			assertThat(entry.outcome()).isEqualTo(COMPENSATED);
			assertThat(entry.detail()).isEqualTo("payment failed upstream");
		});
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=SagaLogTest`
Expected: COMPILATION FAILURE — `SagaLog`, `SagaLogEntry` do not exist yet.

- [ ] **Step 3: Create the event records**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/CheckoutRequested.java`:

```java
package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record CheckoutRequested(String orderId, String customerId, List<OrderLine> items, SagaStep failAt) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderCreated.java`:

```java
package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record OrderCreated(String orderId, List<OrderLine> items, SagaStep failAt) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReserved.java`:

```java
package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.SagaStep;

public record InventoryReserved(String orderId, SagaStep failAt) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReservationFailed.java`:

```java
package com.testingai.saga.choreography.event;

public record InventoryReservationFailed(String orderId, String reason) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentProcessed.java`:

```java
package com.testingai.saga.choreography.event;

import com.testingai.saga.domain.SagaStep;

public record PaymentProcessed(String orderId, SagaStep failAt) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentFailed.java`:

```java
package com.testingai.saga.choreography.event;

public record PaymentFailed(String orderId, String reason) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/PaymentRefunded.java`:

```java
package com.testingai.saga.choreography.event;

public record PaymentRefunded(String orderId) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/ShipmentArranged.java`:

```java
package com.testingai.saga.choreography.event;

public record ShipmentArranged(String orderId) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/ShipmentFailed.java`:

```java
package com.testingai.saga.choreography.event;

public record ShipmentFailed(String orderId, String reason) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/InventoryReleased.java`:

```java
package com.testingai.saga.choreography.event;

public record InventoryReleased(String orderId) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderConfirmed.java`:

```java
package com.testingai.saga.choreography.event;

public record OrderConfirmed(String orderId) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/event/OrderCancelled.java`:

```java
package com.testingai.saga.choreography.event;

public record OrderCancelled(String orderId, String reason) {
}
```

- [ ] **Step 4: Implement `SagaLogEntry` and `SagaLog`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/SagaLogEntry.java`:

```java
package com.testingai.saga.choreography;

import java.time.Instant;

public record SagaLogEntry(String step, Outcome outcome, String detail, Instant timestamp) {

	public enum Outcome {
		SUCCEEDED,
		FAILED,
		COMPENSATED
	}
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/SagaLog.java`:

```java
package com.testingai.saga.choreography;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SagaLog {

	private final Map<String, List<SagaLogEntry>> entriesByOrderId = new ConcurrentHashMap<>();

	public void append(String orderId, String step, SagaLogEntry.Outcome outcome, String detail) {
		entriesByOrderId.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>())
				.add(new SagaLogEntry(step, outcome, detail, Instant.now()));
	}

	public List<SagaLogEntry> timelineFor(String orderId) {
		return entriesByOrderId.getOrDefault(orderId, List.of());
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SagaLogTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/ \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/SagaLogTest.java
git commit -m "feat(saga): add choreography events and SagaLog"
```

---

### Task 5: Choreography `OrderParticipant`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/OrderParticipant.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/OrderParticipantTest.java`

**Interfaces:**
- Consumes: `SagaLog` (Task 4), event records (Task 4), `SagaStatus` (Task 3).
- Produces: `OrderParticipant` with `void onCheckoutRequested(CheckoutRequested)`, `void onShipmentArranged(ShipmentArranged)`, `void onInventoryReservationFailed(InventoryReservationFailed)`, `void onInventoryReleased(InventoryReleased)`, `SagaStatus statusOf(String orderId)` — the latter consumed by `DemoController` (Task 11) and `SagaIntegrationTest` (Task 11).

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/OrderParticipantTest.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCancelled;
import com.testingai.saga.choreography.event.OrderConfirmed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.domain.SagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private OrderParticipant orderParticipant;

	@BeforeEach
	void setUp() {
		orderParticipant = new OrderParticipant(publisher, new SagaLog());
	}

	@Test
	void onCheckoutRequested_shouldCreateOrderAndPublishOrderCreated() {
		orderParticipant.onCheckoutRequested(new CheckoutRequested("order-1", "customer-1", List.of(), null));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.PENDING);
		verify(publisher).publishEvent(new OrderCreated("order-1", List.of(), null));
	}

	@Test
	void onShipmentArranged_shouldConfirmOrderAndPublishOrderConfirmed() {
		orderParticipant.onShipmentArranged(new ShipmentArranged("order-1"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CONFIRMED);
		verify(publisher).publishEvent(new OrderConfirmed("order-1"));
	}

	@Test
	void onInventoryReservationFailed_shouldCancelOrderDirectly() {
		orderParticipant.onInventoryReservationFailed(new InventoryReservationFailed("order-1", "out of stock"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CANCELLED);
		verify(publisher).publishEvent(new OrderCancelled("order-1", "out of stock"));
	}

	@Test
	void onInventoryReleased_shouldCancelOrderAfterCompensation() {
		orderParticipant.onInventoryReleased(new InventoryReleased("order-1"));

		assertThat(orderParticipant.statusOf("order-1")).isEqualTo(SagaStatus.CANCELLED);
		verify(publisher).publishEvent(new OrderCancelled("order-1", "compensated after a downstream step failed"));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=OrderParticipantTest`
Expected: COMPILATION FAILURE — `OrderParticipant` does not exist yet.

- [ ] **Step 3: Implement `OrderParticipant`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/OrderParticipant.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCancelled;
import com.testingai.saga.choreography.event.OrderConfirmed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.domain.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class OrderParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Map<String, SagaStatus> statusByOrderId = new ConcurrentHashMap<>();

	@EventListener
	public void onCheckoutRequested(CheckoutRequested event) {
		statusByOrderId.put(event.orderId(), SagaStatus.PENDING);
		sagaLog.append(event.orderId(), "ORDER_CREATED", SUCCEEDED, null);
		publisher.publishEvent(new OrderCreated(event.orderId(), event.items(), event.failAt()));
	}

	@EventListener
	public void onShipmentArranged(ShipmentArranged event) {
		statusByOrderId.put(event.orderId(), SagaStatus.CONFIRMED);
		sagaLog.append(event.orderId(), "ORDER_CONFIRMED", SUCCEEDED, null);
		publisher.publishEvent(new OrderConfirmed(event.orderId()));
	}

	@EventListener
	public void onInventoryReservationFailed(InventoryReservationFailed event) {
		cancel(event.orderId(), event.reason());
	}

	@EventListener
	public void onInventoryReleased(InventoryReleased event) {
		cancel(event.orderId(), "compensated after a downstream step failed");
	}

	public SagaStatus statusOf(String orderId) {
		return statusByOrderId.get(orderId);
	}

	private void cancel(String orderId, String reason) {
		statusByOrderId.put(orderId, SagaStatus.CANCELLED);
		sagaLog.append(orderId, "ORDER_CANCELLED", FAILED, reason);
		publisher.publishEvent(new OrderCancelled(orderId, reason));
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=OrderParticipantTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/OrderParticipant.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/OrderParticipantTest.java
git commit -m "feat(saga): add choreography OrderParticipant"
```

---

### Task 6: Choreography `InventoryParticipant`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/InventoryParticipant.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/InventoryParticipantTest.java`

**Interfaces:**
- Consumes: `SagaLog`, event records (Task 4), `SagaStep` (Task 3).
- Produces: `InventoryParticipant` with `void onOrderCreated(OrderCreated)`, `void onPaymentFailed(PaymentFailed)`, `void onPaymentRefunded(PaymentRefunded)`, `boolean hasReservation(String orderId)`.

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/InventoryParticipantTest.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private InventoryParticipant inventoryParticipant;

	@BeforeEach
	void setUp() {
		inventoryParticipant = new InventoryParticipant(publisher, new SagaLog());
	}

	@Test
	void onOrderCreated_shouldReserveAndPublishInventoryReservedWhenNotToldToFail() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), null));

		assertThat(inventoryParticipant.hasReservation("order-1")).isTrue();
		verify(publisher).publishEvent(new InventoryReserved("order-1", null));
	}

	@Test
	void onOrderCreated_shouldPublishInventoryReservationFailedWhenToldToFail() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.RESERVE_INVENTORY));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReservationFailed("order-1", "insufficient stock (simulated)"));
	}

	@Test
	void onPaymentFailed_shouldReleaseReservationAndPublishInventoryReleased() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.PROCESS_PAYMENT));

		inventoryParticipant.onPaymentFailed(new PaymentFailed("order-1", "card declined (simulated)"));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReleased("order-1"));
	}

	@Test
	void onPaymentRefunded_shouldReleaseReservationAndPublishInventoryReleased() {
		inventoryParticipant.onOrderCreated(new OrderCreated("order-1", List.of(), SagaStep.ARRANGE_SHIPPING));

		inventoryParticipant.onPaymentRefunded(new PaymentRefunded("order-1"));

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
		verify(publisher).publishEvent(new InventoryReleased("order-1"));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=InventoryParticipantTest`
Expected: COMPILATION FAILURE — `InventoryParticipant` does not exist yet.

- [ ] **Step 3: Implement `InventoryParticipant`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/InventoryParticipant.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReleased;
import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.InventoryReservationFailed;
import com.testingai.saga.choreography.event.OrderCreated;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.COMPENSATED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class InventoryParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Set<String> reservedOrderIds = ConcurrentHashMap.newKeySet();

	@EventListener
	public void onOrderCreated(OrderCreated event) {
		if (event.failAt() == SagaStep.RESERVE_INVENTORY) {
			String reason = "insufficient stock (simulated)";
			sagaLog.append(event.orderId(), "INVENTORY_RESERVATION_FAILED", FAILED, reason);
			publisher.publishEvent(new InventoryReservationFailed(event.orderId(), reason));
			return;
		}
		reservedOrderIds.add(event.orderId());
		sagaLog.append(event.orderId(), "INVENTORY_RESERVED", SUCCEEDED, null);
		publisher.publishEvent(new InventoryReserved(event.orderId(), event.failAt()));
	}

	@EventListener
	public void onPaymentFailed(PaymentFailed event) {
		release(event.orderId());
	}

	@EventListener
	public void onPaymentRefunded(PaymentRefunded event) {
		release(event.orderId());
	}

	public boolean hasReservation(String orderId) {
		return reservedOrderIds.contains(orderId);
	}

	private void release(String orderId) {
		reservedOrderIds.remove(orderId);
		sagaLog.append(orderId, "INVENTORY_RELEASED", COMPENSATED, null);
		publisher.publishEvent(new InventoryReleased(orderId));
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=InventoryParticipantTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/InventoryParticipant.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/InventoryParticipantTest.java
git commit -m "feat(saga): add choreography InventoryParticipant"
```

---

### Task 7: Choreography `PaymentParticipant`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/PaymentParticipant.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/PaymentParticipantTest.java`

**Interfaces:**
- Consumes: `SagaLog`, event records (Task 4), `SagaStep` (Task 3).
- Produces: `PaymentParticipant` with `void onInventoryReserved(InventoryReserved)`, `void onShipmentFailed(ShipmentFailed)`, `boolean hasCharge(String orderId)`.

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/PaymentParticipantTest.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private PaymentParticipant paymentParticipant;

	@BeforeEach
	void setUp() {
		paymentParticipant = new PaymentParticipant(publisher, new SagaLog());
	}

	@Test
	void onInventoryReserved_shouldChargeAndPublishPaymentProcessedWhenNotToldToFail() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", null));

		assertThat(paymentParticipant.hasCharge("order-1")).isTrue();
		verify(publisher).publishEvent(new PaymentProcessed("order-1", null));
	}

	@Test
	void onInventoryReserved_shouldPublishPaymentFailedWhenToldToFail() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", SagaStep.PROCESS_PAYMENT));

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
		verify(publisher).publishEvent(new PaymentFailed("order-1", "card declined (simulated)"));
	}

	@Test
	void onShipmentFailed_shouldRefundAndPublishPaymentRefunded() {
		paymentParticipant.onInventoryReserved(new InventoryReserved("order-1", SagaStep.ARRANGE_SHIPPING));

		paymentParticipant.onShipmentFailed(new ShipmentFailed("order-1", "carrier unavailable (simulated)"));

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
		verify(publisher).publishEvent(new PaymentRefunded("order-1"));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=PaymentParticipantTest`
Expected: COMPILATION FAILURE — `PaymentParticipant` does not exist yet.

- [ ] **Step 3: Implement `PaymentParticipant`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/PaymentParticipant.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.InventoryReserved;
import com.testingai.saga.choreography.event.PaymentFailed;
import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.PaymentRefunded;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.COMPENSATED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class PaymentParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final Set<String> chargedOrderIds = ConcurrentHashMap.newKeySet();

	@EventListener
	public void onInventoryReserved(InventoryReserved event) {
		if (event.failAt() == SagaStep.PROCESS_PAYMENT) {
			String reason = "card declined (simulated)";
			sagaLog.append(event.orderId(), "PAYMENT_FAILED", FAILED, reason);
			publisher.publishEvent(new PaymentFailed(event.orderId(), reason));
			return;
		}
		chargedOrderIds.add(event.orderId());
		sagaLog.append(event.orderId(), "PAYMENT_PROCESSED", SUCCEEDED, null);
		publisher.publishEvent(new PaymentProcessed(event.orderId(), event.failAt()));
	}

	@EventListener
	public void onShipmentFailed(ShipmentFailed event) {
		chargedOrderIds.remove(event.orderId());
		sagaLog.append(event.orderId(), "PAYMENT_REFUNDED", COMPENSATED, null);
		publisher.publishEvent(new PaymentRefunded(event.orderId()));
	}

	public boolean hasCharge(String orderId) {
		return chargedOrderIds.contains(orderId);
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=PaymentParticipantTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/PaymentParticipant.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/PaymentParticipantTest.java
git commit -m "feat(saga): add choreography PaymentParticipant"
```

---

### Task 8: Choreography `ShippingParticipant`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/ShippingParticipant.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/ShippingParticipantTest.java`

**Interfaces:**
- Consumes: `SagaLog`, event records (Task 4), `SagaStep` (Task 3).
- Produces: `ShippingParticipant` with `void onPaymentProcessed(PaymentProcessed)` — completes the choreography chain; from this task on, all four choreography participants exist and the full happy-path and failure cascades described in the design spec are wired end-to-end via Spring's event bus.

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/ShippingParticipantTest.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShippingParticipantTest {

	@Mock
	private ApplicationEventPublisher publisher;

	private ShippingParticipant shippingParticipant;

	@BeforeEach
	void setUp() {
		shippingParticipant = new ShippingParticipant(publisher, new SagaLog());
	}

	@Test
	void onPaymentProcessed_shouldPublishShipmentArrangedWhenNotToldToFail() {
		shippingParticipant.onPaymentProcessed(new PaymentProcessed("order-1", null));

		verify(publisher).publishEvent(new ShipmentArranged("order-1"));
	}

	@Test
	void onPaymentProcessed_shouldPublishShipmentFailedWhenToldToFail() {
		shippingParticipant.onPaymentProcessed(new PaymentProcessed("order-1", SagaStep.ARRANGE_SHIPPING));

		verify(publisher).publishEvent(new ShipmentFailed("order-1", "carrier unavailable (simulated)"));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=ShippingParticipantTest`
Expected: COMPILATION FAILURE — `ShippingParticipant` does not exist yet.

- [ ] **Step 3: Implement `ShippingParticipant`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/ShippingParticipant.java`:

```java
package com.testingai.saga.choreography;

import com.testingai.saga.choreography.event.PaymentProcessed;
import com.testingai.saga.choreography.event.ShipmentArranged;
import com.testingai.saga.choreography.event.ShipmentFailed;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.testingai.saga.choreography.SagaLogEntry.Outcome.FAILED;
import static com.testingai.saga.choreography.SagaLogEntry.Outcome.SUCCEEDED;

@Component
@RequiredArgsConstructor
public class ShippingParticipant {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;

	@EventListener
	public void onPaymentProcessed(PaymentProcessed event) {
		if (event.failAt() == SagaStep.ARRANGE_SHIPPING) {
			String reason = "carrier unavailable (simulated)";
			sagaLog.append(event.orderId(), "SHIPMENT_FAILED", FAILED, reason);
			publisher.publishEvent(new ShipmentFailed(event.orderId(), reason));
			return;
		}
		sagaLog.append(event.orderId(), "SHIPMENT_ARRANGED", SUCCEEDED, null);
		publisher.publishEvent(new ShipmentArranged(event.orderId()));
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=ShippingParticipantTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full choreography test suite**

Run: `mvn test -Dtest=SagaLogTest,OrderParticipantTest,InventoryParticipantTest,PaymentParticipantTest,ShippingParticipantTest`
Expected: `BUILD SUCCESS`, all choreography tests passing together.

- [ ] **Step 6: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/choreography/ShippingParticipant.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/choreography/ShippingParticipantTest.java
git commit -m "feat(saga): add choreography ShippingParticipant, completing the event chain"
```

---

### Task 9: Orchestration domain and participants

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/StepOutcome.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaResult.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/InventoryParticipant.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/PaymentParticipant.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/ShippingParticipant.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/InventoryParticipantTest.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/PaymentParticipantTest.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/ShippingParticipantTest.java`

**Interfaces:**
- Consumes: `SagaStep`, `SagaStatus` (Task 3).
- Produces: `sealed interface StepOutcome permits StepOutcome.Success, StepOutcome.Failure` with `record Success(SagaStep step)` and `record Failure(SagaStep step, String reason)`; `record SagaResult(String orderId, SagaStatus status, SagaStep failedStep, List<SagaStep> compensatedSteps)`; three participant classes each with a step method (`reserve`/`charge`/`arrange`, signature `StepOutcome method(String orderId, SagaStep failAt)`) and `void compensate(String orderId)` — consumed by `SagaOrchestrator` (Task 10).

This task bundles all three orchestration participants and `StepOutcome`/`SagaResult` in one pass — each participant is the same tiny shape (fail-if-told-to / else record success, and a compensate method that undoes it), so splitting further would mean rejecting one trivial participant while approving its neighbor.

- [ ] **Step 1: Write the failing tests**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/InventoryParticipantTest.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryParticipantTest {

	private final InventoryParticipant inventoryParticipant = new InventoryParticipant();

	@Test
	void reserve_shouldSucceedAndRecordReservationWhenNotToldToFail() {
		StepOutcome outcome = inventoryParticipant.reserve("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		assertThat(inventoryParticipant.hasReservation("order-1")).isTrue();
	}

	@Test
	void reserve_shouldFailAndRecordNoReservationWhenToldToFail() {
		StepOutcome outcome = inventoryParticipant.reserve("order-1", SagaStep.RESERVE_INVENTORY);

		assertThat(outcome)
				.isEqualTo(new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)"));
		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveReservation() {
		inventoryParticipant.reserve("order-1", null);

		inventoryParticipant.compensate("order-1");

		assertThat(inventoryParticipant.hasReservation("order-1")).isFalse();
	}
}
```

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/PaymentParticipantTest.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentParticipantTest {

	private final PaymentParticipant paymentParticipant = new PaymentParticipant();

	@Test
	void charge_shouldSucceedAndRecordChargeWhenNotToldToFail() {
		StepOutcome outcome = paymentParticipant.charge("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		assertThat(paymentParticipant.hasCharge("order-1")).isTrue();
	}

	@Test
	void charge_shouldFailAndRecordNoChargeWhenToldToFail() {
		StepOutcome outcome = paymentParticipant.charge("order-1", SagaStep.PROCESS_PAYMENT);

		assertThat(outcome).isEqualTo(new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)"));
		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveCharge() {
		paymentParticipant.charge("order-1", null);

		paymentParticipant.compensate("order-1");

		assertThat(paymentParticipant.hasCharge("order-1")).isFalse();
	}
}
```

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/ShippingParticipantTest.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingParticipantTest {

	private final ShippingParticipant shippingParticipant = new ShippingParticipant();

	@Test
	void arrange_shouldSucceedAndRecordArrangementWhenNotToldToFail() {
		StepOutcome outcome = shippingParticipant.arrange("order-1", null);

		assertThat(outcome).isEqualTo(new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING));
		assertThat(shippingParticipant.hasArrangement("order-1")).isTrue();
	}

	@Test
	void arrange_shouldFailAndRecordNoArrangementWhenToldToFail() {
		StepOutcome outcome = shippingParticipant.arrange("order-1", SagaStep.ARRANGE_SHIPPING);

		assertThat(outcome)
				.isEqualTo(new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)"));
		assertThat(shippingParticipant.hasArrangement("order-1")).isFalse();
	}

	@Test
	void compensate_shouldRemoveArrangement() {
		shippingParticipant.arrange("order-1", null);

		shippingParticipant.compensate("order-1");

		assertThat(shippingParticipant.hasArrangement("order-1")).isFalse();
	}
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn test -Dtest=com.testingai.saga.orchestration.*`
Expected: COMPILATION FAILURE — none of the orchestration classes exist yet.

- [ ] **Step 3: Implement `StepOutcome` and `SagaResult`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/StepOutcome.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;

public sealed interface StepOutcome permits StepOutcome.Success, StepOutcome.Failure {

	record Success(SagaStep step) implements StepOutcome {
	}

	record Failure(SagaStep step, String reason) implements StepOutcome {
	}
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaResult.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;

import java.util.List;

public record SagaResult(String orderId, SagaStatus status, SagaStep failedStep, List<SagaStep> compensatedSteps) {
}
```

- [ ] **Step 4: Implement the three participants**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/InventoryParticipant.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InventoryParticipant {

	private final Set<String> reservedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome reserve(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.RESERVE_INVENTORY) {
			return new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)");
		}
		reservedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.RESERVE_INVENTORY);
	}

	public void compensate(String orderId) {
		reservedOrderIds.remove(orderId);
	}

	public boolean hasReservation(String orderId) {
		return reservedOrderIds.contains(orderId);
	}
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/PaymentParticipant.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentParticipant {

	private final Set<String> chargedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome charge(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.PROCESS_PAYMENT) {
			return new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)");
		}
		chargedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.PROCESS_PAYMENT);
	}

	public void compensate(String orderId) {
		chargedOrderIds.remove(orderId);
	}

	public boolean hasCharge(String orderId) {
		return chargedOrderIds.contains(orderId);
	}
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/ShippingParticipant.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.SagaStep;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShippingParticipant {

	private final Set<String> arrangedOrderIds = ConcurrentHashMap.newKeySet();

	public StepOutcome arrange(String orderId, SagaStep failAt) {
		if (failAt == SagaStep.ARRANGE_SHIPPING) {
			return new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)");
		}
		arrangedOrderIds.add(orderId);
		return new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING);
	}

	public void compensate(String orderId) {
		arrangedOrderIds.remove(orderId);
	}

	public boolean hasArrangement(String orderId) {
		return arrangedOrderIds.contains(orderId);
	}
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=com.testingai.saga.orchestration.*`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/StepOutcome.java \
  distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaResult.java \
  distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/InventoryParticipant.java \
  distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/PaymentParticipant.java \
  distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/ShippingParticipant.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/
git commit -m "feat(saga): add orchestration StepOutcome/SagaResult and the three participants"
```

---

### Task 10: `SagaOrchestrator`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaOrchestrator.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/SagaOrchestratorTest.java`

**Interfaces:**
- Consumes: `InventoryParticipant`, `PaymentParticipant`, `ShippingParticipant`, `StepOutcome`, `SagaResult` (Task 9), `CheckoutRequest` (Task 3).
- Produces: `SagaOrchestrator` with `SagaResult checkout(CheckoutRequest request)` — consumed by `DemoController` (Task 11).

- [ ] **Step 1: Write the failing test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/SagaOrchestratorTest.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

	@Mock
	private InventoryParticipant inventoryParticipant;
	@Mock
	private PaymentParticipant paymentParticipant;
	@Mock
	private ShippingParticipant shippingParticipant;

	private SagaOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		orchestrator = new SagaOrchestrator(inventoryParticipant, paymentParticipant, shippingParticipant);
	}

	@Test
	void checkout_shouldConfirmOrderWhenAllStepsSucceed() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		when(shippingParticipant.arrange(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.ARRANGE_SHIPPING));

		SagaResult result = orchestrator.checkout(new CheckoutRequest("customer-1", List.of(), null));

		assertThat(result.status()).isEqualTo(SagaStatus.CONFIRMED);
		assertThat(result.failedStep()).isNull();
		assertThat(result.compensatedSteps()).isEmpty();
		InOrder order = inOrder(inventoryParticipant, paymentParticipant, shippingParticipant);
		order.verify(inventoryParticipant).reserve(anyString(), any());
		order.verify(paymentParticipant).charge(anyString(), any());
		order.verify(shippingParticipant).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCancelWithoutCompensationWhenInventoryFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.RESERVE_INVENTORY, "insufficient stock (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.RESERVE_INVENTORY));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
		assertThat(result.compensatedSteps()).isEmpty();
		verify(paymentParticipant, never()).charge(anyString(), any());
		verify(shippingParticipant, never()).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCompensateInventoryWhenPaymentFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.PROCESS_PAYMENT, "card declined (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.PROCESS_PAYMENT));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);
		assertThat(result.compensatedSteps()).containsExactly(SagaStep.RESERVE_INVENTORY);
		verify(inventoryParticipant).compensate(anyString());
		verify(shippingParticipant, never()).arrange(anyString(), any());
	}

	@Test
	void checkout_shouldCompensatePaymentThenInventoryWhenShippingFails() {
		when(inventoryParticipant.reserve(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.RESERVE_INVENTORY));
		when(paymentParticipant.charge(anyString(), any()))
				.thenReturn(new StepOutcome.Success(SagaStep.PROCESS_PAYMENT));
		when(shippingParticipant.arrange(anyString(), any()))
				.thenReturn(new StepOutcome.Failure(SagaStep.ARRANGE_SHIPPING, "carrier unavailable (simulated)"));

		SagaResult result = orchestrator
				.checkout(new CheckoutRequest("customer-1", List.of(), SagaStep.ARRANGE_SHIPPING));

		assertThat(result.status()).isEqualTo(SagaStatus.CANCELLED);
		assertThat(result.failedStep()).isEqualTo(SagaStep.ARRANGE_SHIPPING);
		assertThat(result.compensatedSteps()).containsExactly(SagaStep.PROCESS_PAYMENT, SagaStep.RESERVE_INVENTORY);
		InOrder order = inOrder(paymentParticipant, inventoryParticipant);
		order.verify(paymentParticipant).compensate(anyString());
		order.verify(inventoryParticipant).compensate(anyString());
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=SagaOrchestratorTest`
Expected: COMPILATION FAILURE — `SagaOrchestrator` does not exist yet.

- [ ] **Step 3: Implement `SagaOrchestrator`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaOrchestrator.java`:

```java
package com.testingai.saga.orchestration;

import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.domain.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

	private final InventoryParticipant inventoryParticipant;
	private final PaymentParticipant paymentParticipant;
	private final ShippingParticipant shippingParticipant;

	public SagaResult checkout(CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		List<SagaStep> completedSteps = new ArrayList<>();

		StepOutcome inventoryOutcome = inventoryParticipant.reserve(orderId, request.failAt());
		if (inventoryOutcome instanceof StepOutcome.Failure failure) {
			return new SagaResult(orderId, SagaStatus.CANCELLED, failure.step(), List.of());
		}
		completedSteps.add(SagaStep.RESERVE_INVENTORY);

		StepOutcome paymentOutcome = paymentParticipant.charge(orderId, request.failAt());
		if (paymentOutcome instanceof StepOutcome.Failure failure) {
			return compensate(orderId, failure, completedSteps);
		}
		completedSteps.add(SagaStep.PROCESS_PAYMENT);

		StepOutcome shippingOutcome = shippingParticipant.arrange(orderId, request.failAt());
		if (shippingOutcome instanceof StepOutcome.Failure failure) {
			return compensate(orderId, failure, completedSteps);
		}
		completedSteps.add(SagaStep.ARRANGE_SHIPPING);

		return new SagaResult(orderId, SagaStatus.CONFIRMED, null, List.of());
	}

	private SagaResult compensate(String orderId, StepOutcome.Failure failure, List<SagaStep> completedSteps) {
		List<SagaStep> compensatedSteps = new ArrayList<>();
		for (int i = completedSteps.size() - 1; i >= 0; i--) {
			SagaStep step = completedSteps.get(i);
			switch (step) {
				case RESERVE_INVENTORY -> inventoryParticipant.compensate(orderId);
				case PROCESS_PAYMENT -> paymentParticipant.compensate(orderId);
				case ARRANGE_SHIPPING -> shippingParticipant.compensate(orderId);
			}
			compensatedSteps.add(step);
		}
		return new SagaResult(orderId, SagaStatus.CANCELLED, failure.step(), compensatedSteps);
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=SagaOrchestratorTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/orchestration/SagaOrchestrator.java \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/orchestration/SagaOrchestratorTest.java
git commit -m "feat(saga): add SagaOrchestrator with reverse-order compensation"
```

---

### Task 11: `DemoController` and end-to-end `SagaIntegrationTest`

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/CheckoutResponse.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/ChoreographyOrderView.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/DemoExceptionHandler.java`
- Create: `distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/DemoController.java`
- Test: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/controller/SagaIntegrationTest.java`

**Interfaces:**
- Consumes: `SagaLog`, `OrderParticipant`, all four choreography participants (Tasks 4–8); `SagaOrchestrator`, `SagaResult` (Tasks 9–10); `CheckoutRequest` (Task 3).
- Produces: `POST /demo/saga/choreography/checkout` (202, body `CheckoutResponse{orderId}`), `GET /demo/saga/choreography/orders/{orderId}` (200 with `ChoreographyOrderView{orderId, status, timeline}`, or 404), `POST /demo/saga/orchestration/checkout` (200, body `SagaResult`).

- [ ] **Step 1: Write the failing integration test**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/controller/SagaIntegrationTest.java`:

```java
package com.testingai.saga.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.saga.choreography.InventoryParticipant;
import com.testingai.saga.choreography.OrderParticipant;
import com.testingai.saga.choreography.PaymentParticipant;
import com.testingai.saga.choreography.SagaLog;
import com.testingai.saga.choreography.ShippingParticipant;
import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.OrderLine;
import com.testingai.saga.domain.SagaStep;
import com.testingai.saga.orchestration.SagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import({ OrderParticipant.class, InventoryParticipant.class, PaymentParticipant.class, ShippingParticipant.class,
		SagaLog.class, SagaOrchestrator.class, com.testingai.saga.orchestration.InventoryParticipant.class,
		com.testingai.saga.orchestration.PaymentParticipant.class,
		com.testingai.saga.orchestration.ShippingParticipant.class })
class SagaIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void choreographyCheckout_happyPath_shouldConfirmOrder() throws Exception {
		String orderId = checkoutChoreography(null);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.timeline", hasSize(5)))
				.andExpect(jsonPath("$.timeline[4].step").value("ORDER_CONFIRMED"));
	}

	@Test
	void choreographyCheckout_paymentFailure_shouldCascadeCompensationAndCancelOrder() throws Exception {
		String orderId = checkoutChoreography(SagaStep.PROCESS_PAYMENT);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.timeline[*].step", contains("ORDER_CREATED", "INVENTORY_RESERVED",
						"PAYMENT_FAILED", "INVENTORY_RELEASED", "ORDER_CANCELLED")));
	}

	@Test
	void choreographyCheckout_shippingFailure_shouldCascadeCompensationThroughPaymentAndInventory() throws Exception {
		String orderId = checkoutChoreography(SagaStep.ARRANGE_SHIPPING);

		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", orderId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.timeline[*].step", contains("ORDER_CREATED", "INVENTORY_RESERVED",
						"PAYMENT_PROCESSED", "SHIPMENT_FAILED", "PAYMENT_REFUNDED", "INVENTORY_RELEASED",
						"ORDER_CANCELLED")));
	}

	@Test
	void choreographyOrder_unknownOrderId_shouldReturn404() throws Exception {
		mockMvc.perform(get("/demo/saga/choreography/orders/{orderId}", "missing")).andExpect(status().isNotFound());
	}

	@Test
	void orchestrationCheckout_happyPath_shouldConfirmOrder() throws Exception {
		mockMvc.perform(post("/demo/saga/orchestration/checkout").contentType("application/json")
				.content(objectMapper.writeValueAsString(sampleRequest(null)))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.failedStep").value(nullValue()))
				.andExpect(jsonPath("$.compensatedSteps", hasSize(0)));
	}

	@Test
	void orchestrationCheckout_shippingFailure_shouldCompensatePaymentThenInventory() throws Exception {
		mockMvc.perform(post("/demo/saga/orchestration/checkout").contentType("application/json")
				.content(objectMapper.writeValueAsString(sampleRequest(SagaStep.ARRANGE_SHIPPING))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.failedStep").value("ARRANGE_SHIPPING"))
				.andExpect(jsonPath("$.compensatedSteps", contains("PROCESS_PAYMENT", "RESERVE_INVENTORY")));
	}

	private String checkoutChoreography(SagaStep failAt) throws Exception {
		String responseBody = mockMvc
				.perform(post("/demo/saga/choreography/checkout").contentType("application/json")
						.content(objectMapper.writeValueAsString(sampleRequest(failAt))))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(responseBody).get("orderId").asText();
	}

	private CheckoutRequest sampleRequest(SagaStep failAt) {
		return new CheckoutRequest("customer-1", List.of(new OrderLine("p1", 2, new BigDecimal("9.99"))), failAt);
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=SagaIntegrationTest`
Expected: COMPILATION FAILURE — `DemoController`, `CheckoutResponse`, `ChoreographyOrderView` do not exist yet.

- [ ] **Step 3: Implement the response DTOs and exception handler**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/CheckoutResponse.java`:

```java
package com.testingai.saga.controller;

public record CheckoutResponse(String orderId) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/ChoreographyOrderView.java`:

```java
package com.testingai.saga.controller;

import com.testingai.saga.choreography.SagaLogEntry;
import com.testingai.saga.domain.SagaStatus;

import java.util.List;

public record ChoreographyOrderView(String orderId, SagaStatus status, List<SagaLogEntry> timeline) {
}
```

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/DemoExceptionHandler.java`:

```java
package com.testingai.saga.controller;

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

- [ ] **Step 4: Implement `DemoController`**

`distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/DemoController.java`:

```java
package com.testingai.saga.controller;

import com.testingai.saga.choreography.OrderParticipant;
import com.testingai.saga.choreography.SagaLog;
import com.testingai.saga.choreography.SagaLogEntry;
import com.testingai.saga.choreography.event.CheckoutRequested;
import com.testingai.saga.domain.CheckoutRequest;
import com.testingai.saga.domain.SagaStatus;
import com.testingai.saga.orchestration.SagaOrchestrator;
import com.testingai.saga.orchestration.SagaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo/saga")
@RequiredArgsConstructor
public class DemoController {

	private final ApplicationEventPublisher publisher;
	private final SagaLog sagaLog;
	private final OrderParticipant orderParticipant;
	private final SagaOrchestrator orchestrator;

	@PostMapping("/choreography/checkout")
	public ResponseEntity<CheckoutResponse> choreographyCheckout(@RequestBody CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		publisher.publishEvent(
				new CheckoutRequested(orderId, request.customerId(), request.items(), request.failAt()));
		return ResponseEntity.accepted().body(new CheckoutResponse(orderId));
	}

	@GetMapping("/choreography/orders/{orderId}")
	public ResponseEntity<ChoreographyOrderView> choreographyOrder(@PathVariable String orderId) {
		List<SagaLogEntry> timeline = sagaLog.timelineFor(orderId);
		if (timeline.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		SagaStatus status = orderParticipant.statusOf(orderId);
		return ResponseEntity.ok(new ChoreographyOrderView(orderId, status, timeline));
	}

	@PostMapping("/orchestration/checkout")
	public ResponseEntity<SagaResult> orchestrationCheckout(@RequestBody CheckoutRequest request) {
		return ResponseEntity.ok(orchestrator.checkout(request));
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SagaIntegrationTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 6: Run the full module test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all prior test classes (Tasks 2–11) still passing.

- [ ] **Step 7: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/main/java/com/testingai/saga/controller/ \
  distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/controller/
git commit -m "feat(saga): add DemoController wiring both flows, with end-to-end SagaIntegrationTest"
```

---

### Task 12: Gatling load test

**Files:**
- Create: `distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/performance/SagaSimulation.java`

**Interfaces:**
- Consumes: the running `saga-demo` app on `localhost:8089` (Task 11's routes).

- [ ] **Step 1: Write the simulation**

`distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/performance/SagaSimulation.java`:

```java
package com.testingai.saga.performance;

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

public class SagaSimulation extends Simulation {

	private static final String HAPPY_PATH_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}""";

	private static final String PAYMENT_FAILURE_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"PROCESS_PAYMENT"}""";

	private static final String SHIPPING_FAILURE_BODY = """
			{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"ARRANGE_SHIPPING"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8089")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder choreographyScenario = scenario("Choreography Checkout")
			.exec(http("Happy Path Checkout").post("/demo/saga/choreography/checkout").body(StringBody(HAPPY_PATH_BODY))
					.check(status().is(202)).check(jsonPath("$.orderId").saveAs("orderId")))
			.exec(http("Order Timeline").get("/demo/saga/choreography/orders/#{orderId}").check(status().is(200)))
			.exec(http("Forced Payment Failure").post("/demo/saga/choreography/checkout")
					.body(StringBody(PAYMENT_FAILURE_BODY)).check(status().is(202)));

	private final ScenarioBuilder orchestrationScenario = scenario("Orchestration Checkout")
			.exec(http("Happy Path Checkout").post("/demo/saga/orchestration/checkout").body(StringBody(HAPPY_PATH_BODY))
					.check(status().is(200)))
			.exec(http("Forced Shipping Failure").post("/demo/saga/orchestration/checkout")
					.body(StringBody(SHIPPING_FAILURE_BODY)).check(status().is(200)));

	{
		setUp(choreographyScenario.injectOpen(atOnceUsers(10)), orchestrationScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it's excluded from the regular test run**

Run: `mvn test`
Expected: `BUILD SUCCESS`, surefire report does not mention `SagaSimulation` (excluded by the inherited `**/performance/**` surefire pattern).

- [ ] **Step 3: Verify the simulation runs against the live app**

Run (in one terminal): `mvn spring-boot:run`
Run (in a second terminal, once the app is up): `mvn gatling:test`
Expected: Gatling reports `KO 0` for all requests; stop the app afterward (Ctrl+C in the first terminal).

- [ ] **Step 4: Commit**

```bash
git add distributed-transactions/saga/spring-demo/src/test/java/com/testingai/saga/performance/
git commit -m "test(saga): add Gatling SagaSimulation covering both flows"
```

---

### Task 13: READMEs, `CLAUDE.md`, and `.githooks/pre-commit`

**Files:**
- Modify: `distributed-transactions/README.md`
- Create: `distributed-transactions/saga/spring-demo/README.md`
- Modify: `CLAUDE.md`
- Modify: `.githooks/pre-commit`

- [ ] **Step 1: Finalize the category README**

`distributed-transactions/README.md` — replace the Task 1 placeholder with:

```markdown
# Distributed Transactions — Demos

This directory contains runnable demos for distributed-transaction patterns, structured the same way as `../template-engines/`: one Spring Boot demo app per pattern, no external infrastructure required.

| Pattern | Demo | Best fit |
|---|---|---|
| [Saga](saga/) | Choreography + orchestration, e-commerce checkout | Coordinating a multi-step business transaction across services without distributed 2PC |

## Choreography vs. orchestration

| | Choreography | Orchestration |
|---|---|---|
| Coordination | None — each participant reacts to the event before/after it | Central `SagaOrchestrator` calls every participant directly |
| Coupling | Low — participants only know adjacent events | Higher — the orchestrator knows the whole flow |
| Visibility into progress | Indirect — reconstructed from an event/log timeline | Direct — one `SagaResult` returned synchronously |
| Best fit | Independently deployable services, simple per-step logic | Complex flows where the sequence and compensation logic benefit from being explicit in one place |

More distributed-transaction patterns may be added here over time.
```

- [ ] **Step 2: Write the module README**

`distributed-transactions/saga/spring-demo/README.md`:

```markdown
# Saga Pattern Demo

A Spring Boot app demonstrating the Saga pattern for distributed transactions via **both** classic styles, over the same e-commerce checkout scenario (reserve inventory → charge payment → arrange shipping):

- **Choreography** (`com.testingai.saga.choreography`) — participants communicate only through Spring application events (`ApplicationEventPublisher`/`@EventListener`), no coordinator.
- **Orchestration** (`com.testingai.saga.orchestration`) — a central `SagaOrchestrator` calls each participant directly and drives compensation.

No external infrastructure required — everything is in-memory, in-process. Failures (and the resulting compensation cascade) are triggered deterministically via `failAt` on the checkout request, not randomly.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `distributed-transactions/saga/spring-demo/`.

## Run the app

```bash
mvn spring-boot:run
```

## Architecture

### Choreography — happy path

```
POST /demo/saga/choreography/checkout
  → CheckoutRequested
  → OrderCreated            (OrderParticipant)
  → InventoryReserved       (InventoryParticipant)
  → PaymentProcessed        (PaymentParticipant)
  → ShipmentArranged        (ShippingParticipant)
  → OrderConfirmed          (OrderParticipant)
```

### Choreography — failure cascades

A failure at any step cascades **backward** one participant at a time — no participant needs to know the whole saga:

- `failAt=RESERVE_INVENTORY`: `OrderCreated → InventoryReservationFailed → OrderCancelled`
- `failAt=PROCESS_PAYMENT`: `InventoryReserved → PaymentFailed → InventoryReleased → OrderCancelled`
- `failAt=ARRANGE_SHIPPING`: `PaymentProcessed → ShipmentFailed → PaymentRefunded → InventoryReleased → OrderCancelled`

### Orchestration

`SagaOrchestrator` calls `InventoryParticipant.reserve` → `PaymentParticipant.charge` → `ShippingParticipant.arrange` directly. On a `StepOutcome.Failure`, it walks the completed steps **in reverse**, calling `compensate(orderId)` on each, and returns one `SagaResult` synchronously — no separate timeline to query.

## Patterns demonstrated

| Pattern | Where | What it shows |
|---|---|---|
| Choreography | `choreography/` package | Event-driven saga with no central coordinator; compensation propagates via events |
| Orchestration | `orchestration/` package | Central coordinator with explicit, synchronous compensation logic |
| Deterministic failure injection | `CheckoutRequest.failAt` | Reliable, repeatable demoing of the compensation path (vs. random `FailureSimulator`-style injection used elsewhere in this repo) |

## Try it — choreography

```bash
# Happy path
curl -s -X POST http://localhost:8089/demo/saga/choreography/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}'
# => {"orderId":"..."}

# Inspect the timeline (replace ORDER_ID)
curl -s http://localhost:8089/demo/saga/choreography/orders/ORDER_ID | jq

# Force a payment failure and watch the compensation cascade
curl -s -X POST http://localhost:8089/demo/saga/choreography/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"PROCESS_PAYMENT"}'
# then GET the same order — timeline shows ORDER_CREATED, INVENTORY_RESERVED, PAYMENT_FAILED, INVENTORY_RELEASED, ORDER_CANCELLED
```

## Try it — orchestration

```bash
# Happy path — one synchronous result
curl -s -X POST http://localhost:8089/demo/saga/orchestration/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":null}' | jq

# Force a shipping failure — result shows compensatedSteps: [PROCESS_PAYMENT, RESERVE_INVENTORY]
curl -s -X POST http://localhost:8089/demo/saga/orchestration/checkout \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","items":[{"productId":"p1","quantity":2,"unitPrice":9.99}],"failAt":"ARRANGE_SHIPPING"}' | jq
```

## Swagger UI

http://localhost:8089/swagger-ui/index.html

## Run performance tests

```bash
mvn gatling:test
```

Requires the app to already be running in a separate terminal.
```

- [ ] **Step 3: Update `CLAUDE.md`**

In `CLAUDE.md`, find this exact text (the end of the "Template engine demos" section, immediately before "### Backend REST API"):

```
mvn gatling:test                     # load test — requires the app to be running first
```

### Backend REST API
```

Replace it with:

```
mvn gatling:test                     # load test — requires the app to be running first
```

### Saga pattern demo (run from the module root, no docker infrastructure required)

```bash
cd distributed-transactions/saga/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```

### Backend REST API
```

(This is the second occurrence of that `mvn gatling:test` line in the file — the first ends the "NoSQL database demos" section. Anchor on the line immediately followed by `### Backend REST API` to get the right one, which ends the "Template engine demos" section.)

Then find this exact table row:

```
| `template-engines/<engine>/spring-demo/` | Template-engine demo apps, same conventions as `message-brokers/` (currently: Handlebars, FreeMarker) — no external infrastructure required |
| `docker-compose.yml` | Shared infrastructure stack |
```

Replace it with:

```
| `template-engines/<engine>/spring-demo/` | Template-engine demo apps, same conventions as `message-brokers/` (currently: Handlebars, FreeMarker) — no external infrastructure required |
| `distributed-transactions/<pattern>/spring-demo/` | Distributed-transaction pattern demo apps, same conventions as `message-brokers/` (currently: Saga, both choreography and orchestration) — no external infrastructure required |
| `docker-compose.yml` | Shared infrastructure stack |
```

- [ ] **Step 4: Update `.githooks/pre-commit`**

In `.githooks/pre-commit`, find this exact line:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines)/.*\.java$' || true)
```

Replace it with:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions)/.*\.java$' || true)
```

Then find this exact block:

```bash
if echo "$STAGED_JAVA" | grep -q '^template-engines/'; then
    echo "[pre-commit] Applying Spotless formatting to staged template-engines Java files..."
    (cd "$ROOT/template-engines" && mvn spotless:apply --quiet)
fi

# Re-stage the originally staged files (now formatted)
```

Replace it with:

```bash
if echo "$STAGED_JAVA" | grep -q '^template-engines/'; then
    echo "[pre-commit] Applying Spotless formatting to staged template-engines Java files..."
    (cd "$ROOT/template-engines" && mvn spotless:apply --quiet)
fi

if echo "$STAGED_JAVA" | grep -q '^distributed-transactions/'; then
    echo "[pre-commit] Applying Spotless formatting to staged distributed-transactions Java files..."
    (cd "$ROOT/distributed-transactions" && mvn spotless:apply --quiet)
fi

# Re-stage the originally staged files (now formatted)
```

- [ ] **Step 5: Verify the full reactor builds clean**

Run: `cd distributed-transactions && mvn clean package`
Expected: `BUILD SUCCESS`, all test classes from Tasks 2–11 passing (Task 12's `SagaSimulation` excluded).

- [ ] **Step 6: Commit**

```bash
git add distributed-transactions/README.md distributed-transactions/saga/spring-demo/README.md CLAUDE.md .githooks/pre-commit
git commit -m "docs(saga): add module/category READMEs, wire CLAUDE.md and pre-commit hook"
```
