# Camunda Workflow Engine Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `workflow-engines/camunda/spring-demo` module — a Spring Boot app on Camunda 8 (Zeebe) demonstrating BPMN service tasks, an exclusive gateway, a user task, and error-boundary-driven failure routing, over the same order-fulfillment domain as `distributed-transactions/saga`.

**Architecture:** Schema-first-equivalent: a hand-authored BPMN 2.0 process (`order-fulfillment.bpmn`) deployed at startup, executed by a local Camunda 8 self-managed cluster (`camunda/camunda` unified image + Elasticsearch, via `docker compose`). `@JobWorker`-annotated Spring beans (`InventoryWorker`, `PaymentWorker`, `ShippingWorker`) implement the process's service tasks; `DemoController` starts process instances and completes the user task via `CamundaClient`. Failure is deterministic via a `failAt` field on the request (mirroring saga's own convention), not `FailureSimulator`. Automated tests use Camunda's official Testcontainers-backed test library (`camunda-process-test-spring`, `@CamundaSpringProcessTest`) — no manually-running docker-compose needed for `mvn test`, but a working Docker daemon is required.

**Tech Stack:** Java 21, Spring Boot 3.4.4, `io.camunda:camunda-spring-boot-starter:8.8.0`, `io.camunda:camunda-process-test-spring:8.8.0`, Lombok, JUnit 5, Mockito, AssertJ, Gatling.

## Global Constraints

- Module lives at `workflow-engines/camunda/spring-demo/`, under a **new** parent POM `workflow-engines/pom.xml` (mirrors `cqrs-event-sourcing/pom.xml`: Spring Boot 3.4.4 parent, Java 21, Lombok, Gatling plugin, spotless plugin, `install-git-hooks` exec plugin).
- `artifactId`: `camunda-demo`; base package: `com.testingai.camunda`.
- Port `8093` (confirmed free: repo uses 8081–8092, 8094).
- Every API used below (`@JobWorker`, `BpmnError`, `CamundaClient` command builders, `@CamundaSpringProcessTest`, `CamundaAssert`, `ElementSelectors`, `UserTaskSelectors`) has been verified against the real `io.camunda:*:8.8.0` jars (via `javap`), not guessed from docs — use the exact package names and method signatures given in each step.
- Pin **8.8.0** everywhere (`camunda-spring-boot-starter`, `camunda-process-test-spring`, and the `camunda/camunda` Docker image tag) — this is the version verified against.
- `failAt` field convention (not `FailureSimulator`) for deterministic failure injection, matching `distributed-transactions/saga`.
- All fields assigned once must be `private final` (per `.claude/rules/code-review.md`), except the lifecycle-assigned fields rule, which doesn't apply here (no `AutoCloseable` processors in this module).
- No `.toString()` calls on values passed to SLF4J `{}` placeholders.
- Prefer Java 21 idioms (records, pattern matching, text blocks) where natural — domain types are records.
- `mvn test` requires a working Docker daemon (Testcontainers pulls/runs `camunda/camunda:8.8.0` per test class) — the one module in this repo where that's true; called out explicitly in the module README, not silently glossed over.
- Every `.java` file must pass `mvn spotless:apply` (run from `workflow-engines/`) before the final commit, once the pre-commit hook is extended to cover this directory (Task 7).

---

### Task 1: Module scaffolding

**Files:**
- Create: `workflow-engines/pom.xml`
- Create: `workflow-engines/eclipse-formatter.xml` (copy of `cqrs-event-sourcing/eclipse-formatter.xml`)
- Create: `workflow-engines/camunda/spring-demo/pom.xml`
- Create: `workflow-engines/camunda/spring-demo/src/main/resources/application.yml`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/CamundaDemoApplication.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/CamundaDemoApplicationTest.java`

**Interfaces:**
- Produces: a buildable, `@CamundaSpringProcessTest`-testable Spring Boot app. Later tasks add the BPMN process, workers, and controller into this context.

- [ ] **Step 1: Copy the parent POM and formatter config**

`workflow-engines/eclipse-formatter.xml`: copy verbatim from `cqrs-event-sourcing/eclipse-formatter.xml`.

`workflow-engines/pom.xml`:

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
    <artifactId>workflow-engines</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Workflow Engines</name>
    <description>Parent POM for all workflow-engine demo modules</description>

    <modules>
        <module>camunda/spring-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <camunda.version>8.8.0</camunda.version>
        <gatling.version>3.13.1</gatling.version>
        <gatling-maven-plugin.version>4.15.0</gatling-maven-plugin.version>
        <spotless.version>2.43.0</spotless.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
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

- [ ] **Step 2: Create the module POM**

`workflow-engines/camunda/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>workflow-engines</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>camunda-demo</artifactId>
    <name>Camunda Demo</name>
    <description>Learning and demonstration project for Camunda 8 (Zeebe) BPMN workflow patterns</description>

    <dependencies>
        <dependency>
            <groupId>io.camunda</groupId>
            <artifactId>camunda-spring-boot-starter</artifactId>
            <version>${camunda.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>io.camunda</groupId>
            <artifactId>camunda-process-test-spring</artifactId>
            <version>${camunda.version}</version>
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
                    <mainClass>com.testingai.camunda.CamundaDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.camunda.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create `application.yml`**

`workflow-engines/camunda/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8093

camunda:
  client:
    mode: self-managed
    auth:
      method: none
    grpc-address: http://localhost:26500
    rest-address: http://localhost:8080
```

- [ ] **Step 4: Create the application class**

`workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/CamundaDemoApplication.java`:

```java
package com.testingai.camunda;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class CamundaDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(CamundaDemoApplication.class, args);
	}
}
```

(`@Deployment` currently references no files — Task 3 adds `src/main/resources/bpmn/order-fulfillment.bpmn`, at which point this annotation starts deploying it automatically on every app/test startup.)

- [ ] **Step 5: Write the context-loads test**

`workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/CamundaDemoApplicationTest.java`:

```java
package com.testingai.camunda;

import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class CamundaDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

Note: `@CamundaSpringProcessTest` is required on **every** Spring-context test in this module, not just this one — without it, the app's `CamundaClient` bean and `@JobWorker` job-subscription startup have no real broker to connect to and the context fails to start. Plain `@SpringBootTest` alone (as used in the other modules in this repo) does not work here.

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn test -pl camunda/spring-demo -am` from `workflow-engines/` — **requires Docker running** (Testcontainers pulls `camunda/camunda:8.8.0`, which is a multi-hundred-MB image; first run will be slow).
Expected: `BUILD SUCCESS`, 1 test run.

- [ ] **Step 7: Commit**

```bash
git add workflow-engines
git commit -m "feat(workflow-engines): scaffold camunda-demo module"
```

---

### Task 2: Domain records + OrderReadModel

**Files:**
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/OrderLine.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/OrderStep.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/OrderStatus.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/CheckoutRequest.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/OrderView.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain/OrderReadModel.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/domain/OrderReadModelTest.java`

**Interfaces:**
- Produces: `OrderReadModel.register(String orderId, long processInstanceKey, OrderStatus status)`, `.updateStatus(String orderId, OrderStatus status)`, `.recordStepCompleted(String orderId, OrderStep step)`, `.find(String orderId): Optional<OrderView>`. `OrderView(String orderId, long processInstanceKey, OrderStatus status, List<OrderStep> completedSteps)`.

- [ ] **Step 1: Write the domain records**

`OrderLine.java`:

```java
package com.testingai.camunda.domain;

import java.math.BigDecimal;

public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
}
```

`OrderStep.java`:

```java
package com.testingai.camunda.domain;

public enum OrderStep {
	RESERVE_INVENTORY, PROCESS_PAYMENT, ARRANGE_SHIPPING
}
```

`OrderStatus.java`:

```java
package com.testingai.camunda.domain;

public enum OrderStatus {
	PENDING_APPROVAL, IN_PROGRESS, FULFILLED, CANCELLED
}
```

`CheckoutRequest.java`:

```java
package com.testingai.camunda.domain;

import java.util.List;

public record CheckoutRequest(String customerId, List<OrderLine> items, OrderStep failAt) {
}
```

`OrderView.java`:

```java
package com.testingai.camunda.domain;

import java.util.List;

public record OrderView(String orderId, long processInstanceKey, OrderStatus status, List<OrderStep> completedSteps) {
}
```

- [ ] **Step 2: Write the failing test for `OrderReadModel`**

```java
package com.testingai.camunda.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReadModelTest {

	private final OrderReadModel readModel = new OrderReadModel();

	@Test
	void find_returnsEmpty_whenOrderUnknown() {
		assertThat(readModel.find("unknown")).isEmpty();
	}

	@Test
	void register_makesOrderFindable() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		OrderView view = readModel.find("o1").orElseThrow();
		assertThat(view.orderId()).isEqualTo("o1");
		assertThat(view.processInstanceKey()).isEqualTo(123L);
		assertThat(view.status()).isEqualTo(OrderStatus.IN_PROGRESS);
		assertThat(view.completedSteps()).isEmpty();
	}

	@Test
	void updateStatus_changesStatus() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		readModel.updateStatus("o1", OrderStatus.CANCELLED);

		assertThat(readModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void recordStepCompleted_appendsSteps_inOrder() {
		readModel.register("o1", 123L, OrderStatus.IN_PROGRESS);

		readModel.recordStepCompleted("o1", OrderStep.RESERVE_INVENTORY);
		readModel.recordStepCompleted("o1", OrderStep.PROCESS_PAYMENT);

		assertThat(readModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.RESERVE_INVENTORY,
				OrderStep.PROCESS_PAYMENT);
	}
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=OrderReadModelTest`
Expected: FAIL — `OrderReadModel` does not exist.

- [ ] **Step 4: Write `OrderReadModel`**

```java
package com.testingai.camunda.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory read model of order status, updated by the job workers as the BPMN process executes and read back by
 * {@code DemoController}'s status endpoint. No persistence layer, matching {@code saga}/{@code template-engines}'
 * in-memory conventions.
 */
@Component
public class OrderReadModel {

	private final Map<String, Long> processInstanceKeyByOrderId = new ConcurrentHashMap<>();
	private final Map<String, OrderStatus> statusByOrderId = new ConcurrentHashMap<>();
	private final Map<String, List<OrderStep>> completedStepsByOrderId = new ConcurrentHashMap<>();

	public void register(String orderId, long processInstanceKey, OrderStatus status) {
		processInstanceKeyByOrderId.put(orderId, processInstanceKey);
		statusByOrderId.put(orderId, status);
		completedStepsByOrderId.put(orderId, new CopyOnWriteArrayList<>());
	}

	public void updateStatus(String orderId, OrderStatus status) {
		statusByOrderId.put(orderId, status);
	}

	public void recordStepCompleted(String orderId, OrderStep step) {
		completedStepsByOrderId.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>()).add(step);
	}

	public Optional<OrderView> find(String orderId) {
		OrderStatus status = statusByOrderId.get(orderId);
		if (status == null) {
			return Optional.empty();
		}
		Long processInstanceKey = processInstanceKeyByOrderId.get(orderId);
		List<OrderStep> steps = List.copyOf(completedStepsByOrderId.getOrDefault(orderId, List.of()));
		return Optional.of(new OrderView(orderId, processInstanceKey, status, steps));
	}
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=OrderReadModelTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/domain workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/domain
git commit -m "feat(workflow-engines): add order domain records and read model"
```

---

### Task 3: BPMN process + service task workers + start/status endpoints

This is the largest task: authoring the full BPMN diagram (all elements, since a BPMN process is modeled as one coherent unit, not built incrementally test-by-test), the three straight-line workers, and the controller's start/status endpoints. Later tasks (4–5) add tests that exercise the gateway/user-task/error-boundary parts of this same file.

**Files:**
- Create: `workflow-engines/camunda/spring-demo/src/main/resources/bpmn/order-fulfillment.bpmn`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/InventoryWorker.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/PaymentWorker.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/ShippingWorker.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/StartOrderResponse.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/OrderNotFoundException.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/DemoController.java`
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/DemoExceptionHandler.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/InventoryWorkerTest.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/PaymentWorkerTest.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/ShippingWorkerTest.java`
- Test: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `OrderReadModel` (Task 2).
- Produces: `DemoController.startOrder(CheckoutRequest): ResponseEntity<StartOrderResponse>` (`POST /demo/camunda/orders`), `DemoController.getOrder(String orderId): ResponseEntity<OrderView>` (`GET /demo/camunda/orders/{orderId}`). Job types `reserve-inventory`, `release-inventory`, `process-payment`, `arrange-shipping` wired to the BPMN process `order-fulfillment`.

- [ ] **Step 1: Write the BPMN process**

`workflow-engines/camunda/spring-demo/src/main/resources/bpmn/order-fulfillment.bpmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_OrderFulfillment" targetNamespace="http://bpmn.io/schema/bpmn" exporter="techmix-copy" exporterVersion="1.0">

  <bpmn:error id="Error_InventoryUnavailable" name="Inventory Unavailable" errorCode="INVENTORY_UNAVAILABLE" />
  <bpmn:error id="Error_PaymentDeclined" name="Payment Declined" errorCode="PAYMENT_DECLINED" />

  <bpmn:process id="order-fulfillment" name="Order Fulfillment" isExecutable="true">

    <bpmn:startEvent id="StartEvent_OrderPlaced" name="Order Placed">
      <bpmn:outgoing>Flow_StartToReserve</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:serviceTask id="ServiceTask_ReserveInventory" name="Reserve Inventory">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="reserve-inventory" retries="1" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_StartToReserve</bpmn:incoming>
      <bpmn:outgoing>Flow_ReserveToGateway</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:boundaryEvent id="BoundaryEvent_InventoryUnavailable" name="Inventory Unavailable" attachedToRef="ServiceTask_ReserveInventory">
      <bpmn:outgoing>Flow_InventoryErrorToCancelled</bpmn:outgoing>
      <bpmn:errorEventDefinition id="ErrorEventDefinition_InventoryUnavailable" errorRef="Error_InventoryUnavailable" />
    </bpmn:boundaryEvent>

    <bpmn:exclusiveGateway id="Gateway_HighValueOrder" name="High-Value Order?" default="Flow_GatewayToPayment">
      <bpmn:incoming>Flow_ReserveToGateway</bpmn:incoming>
      <bpmn:outgoing>Flow_GatewayToApproval</bpmn:outgoing>
      <bpmn:outgoing>Flow_GatewayToPayment</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:userTask id="UserTask_ApproveOrder" name="Approve Order">
      <bpmn:extensionElements>
        <zeebe:userTask />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_GatewayToApproval</bpmn:incoming>
      <bpmn:outgoing>Flow_ApprovalToDecision</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:exclusiveGateway id="Gateway_ApprovalDecision" name="Approved?" default="Flow_DecisionRejected">
      <bpmn:incoming>Flow_ApprovalToDecision</bpmn:incoming>
      <bpmn:outgoing>Flow_DecisionApproved</bpmn:outgoing>
      <bpmn:outgoing>Flow_DecisionRejected</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:serviceTask id="ServiceTask_ProcessPayment" name="Process Payment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="process-payment" retries="1" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_GatewayToPayment</bpmn:incoming>
      <bpmn:incoming>Flow_DecisionApproved</bpmn:incoming>
      <bpmn:outgoing>Flow_PaymentToShipping</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:boundaryEvent id="BoundaryEvent_PaymentDeclined" name="Payment Declined" attachedToRef="ServiceTask_ProcessPayment">
      <bpmn:outgoing>Flow_PaymentErrorToRelease</bpmn:outgoing>
      <bpmn:errorEventDefinition id="ErrorEventDefinition_PaymentDeclined" errorRef="Error_PaymentDeclined" />
    </bpmn:boundaryEvent>

    <bpmn:serviceTask id="ServiceTask_ArrangeShipping" name="Arrange Shipping">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="arrange-shipping" retries="1" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_PaymentToShipping</bpmn:incoming>
      <bpmn:outgoing>Flow_ShippingToFulfilled</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:endEvent id="EndEvent_OrderFulfilled" name="Order Fulfilled">
      <bpmn:incoming>Flow_ShippingToFulfilled</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:serviceTask id="ServiceTask_ReleaseInventory" name="Release Inventory">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="release-inventory" retries="1" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_PaymentErrorToRelease</bpmn:incoming>
      <bpmn:incoming>Flow_DecisionRejected</bpmn:incoming>
      <bpmn:outgoing>Flow_ReleaseToCancelled</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:endEvent id="EndEvent_OrderCancelled" name="Order Cancelled">
      <bpmn:incoming>Flow_InventoryErrorToCancelled</bpmn:incoming>
      <bpmn:incoming>Flow_ReleaseToCancelled</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="Flow_StartToReserve" sourceRef="StartEvent_OrderPlaced" targetRef="ServiceTask_ReserveInventory" />
    <bpmn:sequenceFlow id="Flow_ReserveToGateway" sourceRef="ServiceTask_ReserveInventory" targetRef="Gateway_HighValueOrder" />
    <bpmn:sequenceFlow id="Flow_GatewayToApproval" sourceRef="Gateway_HighValueOrder" targetRef="UserTask_ApproveOrder">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=totalCents &gt; 50000</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_GatewayToPayment" sourceRef="Gateway_HighValueOrder" targetRef="ServiceTask_ProcessPayment" />
    <bpmn:sequenceFlow id="Flow_ApprovalToDecision" sourceRef="UserTask_ApproveOrder" targetRef="Gateway_ApprovalDecision" />
    <bpmn:sequenceFlow id="Flow_DecisionApproved" sourceRef="Gateway_ApprovalDecision" targetRef="ServiceTask_ProcessPayment">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=approved = true</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_DecisionRejected" sourceRef="Gateway_ApprovalDecision" targetRef="ServiceTask_ReleaseInventory" />
    <bpmn:sequenceFlow id="Flow_PaymentToShipping" sourceRef="ServiceTask_ProcessPayment" targetRef="ServiceTask_ArrangeShipping" />
    <bpmn:sequenceFlow id="Flow_ShippingToFulfilled" sourceRef="ServiceTask_ArrangeShipping" targetRef="EndEvent_OrderFulfilled" />
    <bpmn:sequenceFlow id="Flow_InventoryErrorToCancelled" sourceRef="BoundaryEvent_InventoryUnavailable" targetRef="EndEvent_OrderCancelled" />
    <bpmn:sequenceFlow id="Flow_PaymentErrorToRelease" sourceRef="BoundaryEvent_PaymentDeclined" targetRef="ServiceTask_ReleaseInventory" />
    <bpmn:sequenceFlow id="Flow_ReleaseToCancelled" sourceRef="ServiceTask_ReleaseInventory" targetRef="EndEvent_OrderCancelled" />

  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="order-fulfillment">
      <bpmndi:BPMNShape id="StartEvent_OrderPlaced_di" bpmnElement="StartEvent_OrderPlaced">
        <dc:Bounds x="160" y="142" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_ReserveInventory_di" bpmnElement="ServiceTask_ReserveInventory">
        <dc:Bounds x="250" y="120" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BoundaryEvent_InventoryUnavailable_di" bpmnElement="BoundaryEvent_InventoryUnavailable">
        <dc:Bounds x="282" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_HighValueOrder_di" bpmnElement="Gateway_HighValueOrder">
        <dc:Bounds x="410" y="135" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_ApproveOrder_di" bpmnElement="UserTask_ApproveOrder">
        <dc:Bounds x="410" y="280" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_ApprovalDecision_di" bpmnElement="Gateway_ApprovalDecision">
        <dc:Bounds x="570" y="295" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_ProcessPayment_di" bpmnElement="ServiceTask_ProcessPayment">
        <dc:Bounds x="560" y="120" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BoundaryEvent_PaymentDeclined_di" bpmnElement="BoundaryEvent_PaymentDeclined">
        <dc:Bounds x="592" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_ArrangeShipping_di" bpmnElement="ServiceTask_ArrangeShipping">
        <dc:Bounds x="720" y="120" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_OrderFulfilled_di" bpmnElement="EndEvent_OrderFulfilled">
        <dc:Bounds x="880" y="142" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_ReleaseInventory_di" bpmnElement="ServiceTask_ReleaseInventory">
        <dc:Bounds x="560" y="420" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_OrderCancelled_di" bpmnElement="EndEvent_OrderCancelled">
        <dc:Bounds x="720" y="442" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_StartToReserve_di" bpmnElement="Flow_StartToReserve">
        <di:waypoint x="196" y="160" />
        <di:waypoint x="250" y="160" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ReserveToGateway_di" bpmnElement="Flow_ReserveToGateway">
        <di:waypoint x="350" y="160" />
        <di:waypoint x="410" y="160" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_GatewayToApproval_di" bpmnElement="Flow_GatewayToApproval">
        <di:waypoint x="435" y="185" />
        <di:waypoint x="435" y="280" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_GatewayToPayment_di" bpmnElement="Flow_GatewayToPayment">
        <di:waypoint x="460" y="160" />
        <di:waypoint x="560" y="160" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ApprovalToDecision_di" bpmnElement="Flow_ApprovalToDecision">
        <di:waypoint x="510" y="320" />
        <di:waypoint x="570" y="320" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_DecisionApproved_di" bpmnElement="Flow_DecisionApproved">
        <di:waypoint x="595" y="295" />
        <di:waypoint x="595" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_DecisionRejected_di" bpmnElement="Flow_DecisionRejected">
        <di:waypoint x="595" y="345" />
        <di:waypoint x="595" y="420" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_PaymentToShipping_di" bpmnElement="Flow_PaymentToShipping">
        <di:waypoint x="660" y="160" />
        <di:waypoint x="720" y="160" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ShippingToFulfilled_di" bpmnElement="Flow_ShippingToFulfilled">
        <di:waypoint x="820" y="160" />
        <di:waypoint x="880" y="160" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_InventoryErrorToCancelled_di" bpmnElement="Flow_InventoryErrorToCancelled">
        <di:waypoint x="300" y="218" />
        <di:waypoint x="300" y="460" />
        <di:waypoint x="720" y="460" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_PaymentErrorToRelease_di" bpmnElement="Flow_PaymentErrorToRelease">
        <di:waypoint x="610" y="218" />
        <di:waypoint x="610" y="420" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ReleaseToCancelled_di" bpmnElement="Flow_ReleaseToCancelled">
        <di:waypoint x="660" y="460" />
        <di:waypoint x="720" y="460" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

Note on `ServiceTask_ReleaseInventory` and `EndEvent_OrderCancelled` each having two `<bpmn:incoming>` flows: this is valid BPMN (an implicit merge — the element proceeds whenever *either* incoming flow arrives). `ServiceTask_ReserveInventory`'s own failure path (`BoundaryEvent_InventoryUnavailable`) skips `ServiceTask_ReleaseInventory` and goes straight to `EndEvent_OrderCancelled`, since nothing was ever reserved to release — only the payment-decline and rejection paths (both of which occur *after* a successful reservation) need the release step. This is a deliberate correction versus the design spec's simplified diagram, made during implementation for logical correctness.

- [ ] **Step 2: Write the failing worker tests**

`workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/InventoryWorkerTest.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final InventoryWorker worker = new InventoryWorker(orderReadModel);

	@Test
	void reserveInventory_marksReserved_whenNoFailAt() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		Map<String, Object> result = worker.reserveInventory(job);

		assertThat(result).containsEntry("inventoryReserved", true);
		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.RESERVE_INVENTORY);
	}

	@Test
	void reserveInventory_throwsInventoryUnavailable_whenFailAtMatches() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1", "failAt", "RESERVE_INVENTORY"));

		assertThatThrownBy(() -> worker.reserveInventory(job)).isInstanceOf(BpmnError.class)
				.satisfies(ex -> assertThat(((BpmnError) ex).getErrorCode()).isEqualTo("INVENTORY_UNAVAILABLE"));
		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void releaseInventory_marksOrderCancelled() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		Map<String, Object> result = worker.releaseInventory(job);

		assertThat(result).containsEntry("inventoryReserved", false);
		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);
	}
}
```

`workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/PaymentWorkerTest.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final PaymentWorker worker = new PaymentWorker(orderReadModel);

	@Test
	void processPayment_recordsStep_whenNoFailAt() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		worker.processPayment(job);

		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.PROCESS_PAYMENT);
	}

	@Test
	void processPayment_throwsPaymentDeclined_whenFailAtMatches() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1", "failAt", "PROCESS_PAYMENT"));

		assertThatThrownBy(() -> worker.processPayment(job)).isInstanceOf(BpmnError.class)
				.satisfies(ex -> assertThat(((BpmnError) ex).getErrorCode()).isEqualTo("PAYMENT_DECLINED"));
	}
}
```

`workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/worker/ShippingWorkerTest.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShippingWorkerTest {

	private final OrderReadModel orderReadModel = new OrderReadModel();
	private final ShippingWorker worker = new ShippingWorker(orderReadModel);

	@Test
	void arrangeShipping_marksOrderFulfilled() {
		orderReadModel.register("o1", 1L, OrderStatus.IN_PROGRESS);
		ActivatedJob job = mock(ActivatedJob.class);
		when(job.getVariablesAsMap()).thenReturn(Map.of("orderId", "o1"));

		worker.arrangeShipping(job);

		assertThat(orderReadModel.find("o1").orElseThrow().status()).isEqualTo(OrderStatus.FULFILLED);
		assertThat(orderReadModel.find("o1").orElseThrow().completedSteps()).containsExactly(OrderStep.ARRANGE_SHIPPING);
	}
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=InventoryWorkerTest,PaymentWorkerTest,ShippingWorkerTest`
Expected: FAIL — worker classes don't exist.

- [ ] **Step 4: Write the workers**

`workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/InventoryWorker.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "reserve-inventory")
	public Map<String, Object> reserveInventory(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		String failAt = (String) job.getVariablesAsMap().get("failAt");
		log.info("[reserve-inventory] orderId={}", orderId);
		if (OrderStep.RESERVE_INVENTORY.name().equals(failAt)) {
			log.warn("[reserve-inventory] simulated failure for orderId={}", orderId);
			orderReadModel.updateStatus(orderId, OrderStatus.CANCELLED);
			throw new BpmnError("INVENTORY_UNAVAILABLE", "No stock available for order " + orderId);
		}
		orderReadModel.recordStepCompleted(orderId, OrderStep.RESERVE_INVENTORY);
		return Map.of("inventoryReserved", true);
	}

	@JobWorker(type = "release-inventory")
	public Map<String, Object> releaseInventory(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		log.info("[release-inventory] releasing reserved inventory for orderId={}", orderId);
		orderReadModel.updateStatus(orderId, OrderStatus.CANCELLED);
		return Map.of("inventoryReserved", false);
	}
}
```

`workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/PaymentWorker.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "process-payment")
	public void processPayment(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		String failAt = (String) job.getVariablesAsMap().get("failAt");
		log.info("[process-payment] orderId={}", orderId);
		if (OrderStep.PROCESS_PAYMENT.name().equals(failAt)) {
			log.warn("[process-payment] simulated failure for orderId={}", orderId);
			throw new BpmnError("PAYMENT_DECLINED", "Card declined for order " + orderId);
		}
		orderReadModel.recordStepCompleted(orderId, OrderStep.PROCESS_PAYMENT);
	}
}
```

`workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/worker/ShippingWorker.java`:

```java
package com.testingai.camunda.worker;

import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderStep;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingWorker {

	private final OrderReadModel orderReadModel;

	@JobWorker(type = "arrange-shipping")
	public void arrangeShipping(ActivatedJob job) {
		String orderId = (String) job.getVariablesAsMap().get("orderId");
		log.info("[arrange-shipping] orderId={}", orderId);
		orderReadModel.recordStepCompleted(orderId, OrderStep.ARRANGE_SHIPPING);
		orderReadModel.updateStatus(orderId, OrderStatus.FULFILLED);
	}
}
```

- [ ] **Step 5: Run worker tests to verify they pass**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=InventoryWorkerTest,PaymentWorkerTest,ShippingWorkerTest`
Expected: PASS (7 tests total)

- [ ] **Step 6: Write the failing `DemoIntegrationTest` (happy-path, low-value order only)**

```java
package com.testingai.camunda.controller;

import com.testingai.camunda.domain.CheckoutRequest;
import com.testingai.camunda.domain.OrderLine;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
class DemoIntegrationTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void lowValueOrder_completesWithoutApproval() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		long processInstanceKey = response.getBody().processInstanceKey();

		assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
				.hasCompletedElementsInOrder(byId("StartEvent_OrderPlaced"), byId("ServiceTask_ReserveInventory"),
						byId("Gateway_HighValueOrder"), byId("ServiceTask_ProcessPayment"),
						byId("ServiceTask_ArrangeShipping"), byId("EndEvent_OrderFulfilled"))
				.isCompleted();
	}
}
```

`ProcessInstanceSelectors.byKey(long): ProcessInstanceSelector` and `CamundaAssert.assertThatProcessInstance(ProcessInstanceSelector): ProcessInstanceAssert` are both confirmed against the `camunda-process-test-java:8.8.0` jar — this asserts purely by `processInstanceKey` (obtained from the REST response), no need to keep a live `ProcessInstanceEvent` object around or create a second instance.

- [ ] **Step 7: Write `DemoController` and its supporting types**

`StartOrderResponse.java`:

```java
package com.testingai.camunda.controller;

public record StartOrderResponse(String orderId, long processInstanceKey) {
}
```

`OrderNotFoundException.java`:

```java
package com.testingai.camunda.controller;

public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException(String orderId) {
		super("Unknown order: " + orderId);
	}
}
```

`DemoController.java` (start + status endpoints only — Task 4 adds the approval endpoint):

```java
package com.testingai.camunda.controller;

import com.testingai.camunda.domain.CheckoutRequest;
import com.testingai.camunda.domain.OrderReadModel;
import com.testingai.camunda.domain.OrderStatus;
import com.testingai.camunda.domain.OrderView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Starts and observes order-fulfillment process instances. Job workers ({@code InventoryWorker},
 * {@code PaymentWorker}, {@code ShippingWorker}) do the actual step-by-step work and update {@link OrderReadModel} as
 * they go; this controller only starts instances and reads that model back.
 */
@Slf4j
@RestController
@RequestMapping("/demo/camunda")
@RequiredArgsConstructor
public class DemoController {

	private static final long HIGH_VALUE_THRESHOLD_CENTS = 50_000;
	private static final String PROCESS_ID = "order-fulfillment";

	private final CamundaClient camundaClient;
	private final OrderReadModel orderReadModel;

	@PostMapping("/orders")
	public ResponseEntity<StartOrderResponse> startOrder(@RequestBody CheckoutRequest request) {
		String orderId = UUID.randomUUID().toString();
		long totalCents = request.items().stream().mapToLong(
				item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())).movePointRight(2).longValueExact())
				.sum();

		Map<String, Object> variables = new HashMap<>();
		variables.put("orderId", orderId);
		variables.put("customerId", request.customerId());
		variables.put("totalCents", totalCents);
		if (request.failAt() != null) {
			variables.put("failAt", request.failAt().name());
		}

		OrderStatus initialStatus = totalCents > HIGH_VALUE_THRESHOLD_CENTS ? OrderStatus.PENDING_APPROVAL
				: OrderStatus.IN_PROGRESS;

		ProcessInstanceEvent instance = camundaClient.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID)
				.latestVersion().variables(variables).execute();

		orderReadModel.register(orderId, instance.getProcessInstanceKey(), initialStatus);
		log.info("[startOrder] orderId={} processInstanceKey={} totalCents={}", orderId,
				instance.getProcessInstanceKey(), totalCents);
		return ResponseEntity.ok(new StartOrderResponse(orderId, instance.getProcessInstanceKey()));
	}

	@GetMapping("/orders/{orderId}")
	public ResponseEntity<OrderView> getOrder(@PathVariable String orderId) {
		return orderReadModel.find(orderId).map(ResponseEntity::ok)
				.orElseThrow(() -> new OrderNotFoundException(orderId));
	}
}
```

`DemoExceptionHandler.java`:

```java
package com.testingai.camunda.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(OrderNotFoundException.class)
	public ResponseEntity<String> handleOrderNotFound(OrderNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
	}
}
```

Note: `getOrder`'s `.orElseThrow(...)` inside a method returning `ResponseEntity<OrderView>` via `.map(ResponseEntity::ok)` works because `Optional.orElseThrow` on the `Optional<ResponseEntity<OrderView>>` produced by `.map(...)` throws before ever needing to produce a value — i.e. `orderReadModel.find(orderId)` is `Optional<OrderView>`, `.map(ResponseEntity::ok)` turns it into `Optional<ResponseEntity<OrderView>>`, and `.orElseThrow(...)` on *that* throws when the original was empty. This compiles and behaves correctly; if it reads awkwardly, an equally valid alternative is `OrderView view = orderReadModel.find(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)); return ResponseEntity.ok(view);` — use whichever your linter prefers, both are correct.

- [ ] **Step 8: Run the full test suite for this task**

Run: `mvn test -pl camunda/spring-demo -am`
Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add workflow-engines/camunda/spring-demo/src
git commit -m "feat(workflow-engines): add order-fulfillment BPMN process, service task workers, and start/status endpoints"
```

---

### Task 4: Approval flow (gateway + user task)

**Files:**
- Create: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/ApprovalRequest.java`
- Modify: `workflow-engines/camunda/spring-demo/src/main/java/com/testingai/camunda/controller/DemoController.java`
- Modify: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `CamundaClient.newUserTaskSearchRequest()`, `.newCompleteUserTaskCommand(long)` (verified against the jar in Task 3's research; see Global Constraints).
- Produces: `DemoController.approveOrder(String orderId, ApprovalRequest): ResponseEntity<Void>` (`POST /demo/camunda/orders/{orderId}/approval`).

- [ ] **Step 1: Write the failing integration tests**

Add to `DemoIntegrationTest`:

```java
	@Test
	void highValueOrder_requiresApproval_thenCompletesWhenApproved() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(999.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);
		String orderId = response.getBody().orderId();
		long processInstanceKey = response.getBody().processInstanceKey();

		assertThatUserTask(byProcessInstanceKey(processInstanceKey)).isCreated();

		restTemplate.postForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId + "/approval",
				new ApprovalRequest(true), Void.class);

		// re-fetch the process instance via the client for a fresh assertion snapshot
		assertThatUserTask(byProcessInstanceKey(processInstanceKey)).isCompleted();
	}

	@Test
	void highValueOrder_cancelsWhenRejected() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(999.00))), null);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);
		String orderId = response.getBody().orderId();
		long processInstanceKey = response.getBody().processInstanceKey();

		restTemplate.postForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId + "/approval",
				new ApprovalRequest(false), Void.class);

		ResponseEntity<OrderView> orderView = restTemplate
				.getForEntity("http://localhost:" + port + "/demo/camunda/orders/" + orderId, OrderView.class);
		assertThat(orderView.getBody().status()).isEqualTo(com.testingai.camunda.domain.OrderStatus.CANCELLED);
	}
```

Add imports: `import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;` and `import static io.camunda.process.test.api.assertions.UserTaskSelectors.byProcessInstanceKey;`, plus `import com.testingai.camunda.domain.OrderView;`.

Note: waiting for the process to actually reach `EndEvent_OrderFulfilled`/`EndEvent_OrderCancelled` after these REST calls may need a brief `Thread.sleep(...)` or, better, rely on `CamundaAssert`'s built-in polling/retry behavior (`DEFAULT_ASSERTION_TIMEOUT`/`DEFAULT_ASSERTION_INTERVAL`, confirmed present on `CamundaAssert`) — its assertion methods already retry internally for a few seconds by default, so a plain `assertThatUserTask(...).isCompleted()` right after the REST call should work without a manual sleep. Only add an explicit wait if this proves flaky.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: FAIL — `ApprovalRequest` doesn't exist, `DemoController.approveOrder` doesn't exist.

- [ ] **Step 3: Write `ApprovalRequest`**

```java
package com.testingai.camunda.controller;

public record ApprovalRequest(boolean approved) {
}
```

- [ ] **Step 4: Add the approval endpoint to `DemoController`**

Add this method and the two new imports:

```java
	@PostMapping("/orders/{orderId}/approval")
	public ResponseEntity<Void> approveOrder(@PathVariable String orderId, @RequestBody ApprovalRequest request) {
		OrderView order = orderReadModel.find(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

		UserTask userTask = findPendingUserTask(order.processInstanceKey());

		camundaClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).variable("approved", request.approved())
				.execute();

		log.info("[approveOrder] orderId={} approved={}", orderId, request.approved());
		return ResponseEntity.ok().build();
	}

	/**
	 * The user task search index is populated asynchronously (Zeebe exporter → Elasticsearch), so a search run
	 * immediately after the process instance reaches the user task can momentarily return no results — confirmed by a
	 * live smoke test against a real docker-compose stack, where a same-instant approval call right after
	 * {@code startOrder} got a {@code null} user task (this is why {@code SearchResponse.singleItem()} is avoided
	 * here: it returns {@code null} rather than throwing when the result list is empty). Retries for up to 5 seconds
	 * rather than assuming the index is already caught up.
	 */
	private UserTask findPendingUserTask(long processInstanceKey) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (true) {
			UserTask userTask = camundaClient.newUserTaskSearchRequest()
					.filter(f -> f.processInstanceKey(processInstanceKey).state(UserTaskState.CREATED)).execute()
					.items().stream().findFirst().orElse(null);
			if (userTask != null) {
				return userTask;
			}
			if (Instant.now().isAfter(deadline)) {
				throw new IllegalStateException(
						"No pending user task found for processInstanceKey=" + processInstanceKey);
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Interrupted while waiting for user task for processInstanceKey=" + processInstanceKey, e);
			}
		}
	}
```

Add `import java.time.Duration;` and `import java.time.Instant;` alongside the two imports below.

Imports to add to `DemoController.java`:

```java
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add workflow-engines/camunda/spring-demo/src
git commit -m "feat(workflow-engines): add order approval endpoint (gateway + user task pattern)"
```

---

### Task 5: Error-boundary failure path integration tests

**Files:**
- Modify: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/controller/DemoIntegrationTest.java`

**Interfaces:** None new — exercises the `failAt` mechanism (Task 2/3) through the full deployed process end-to-end.

- [ ] **Step 1: Add the failing tests**

```java
	@Test
	void order_isCancelled_whenInventoryReservationFailsAt() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), OrderStep.RESERVE_INVENTORY);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		ResponseEntity<OrderView> orderView = restTemplate.getForEntity(
				"http://localhost:" + port + "/demo/camunda/orders/" + response.getBody().orderId(), OrderView.class);
		assertThat(orderView.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void order_isCancelled_whenPaymentFailsAt() {
		CheckoutRequest request = new CheckoutRequest("customer-1",
				List.of(new OrderLine("p1", 1, BigDecimal.valueOf(10.00))), OrderStep.PROCESS_PAYMENT);

		ResponseEntity<StartOrderResponse> response = restTemplate
				.postForEntity("http://localhost:" + port + "/demo/camunda/orders", request, StartOrderResponse.class);

		ResponseEntity<OrderView> orderView = restTemplate.getForEntity(
				"http://localhost:" + port + "/demo/camunda/orders/" + response.getBody().orderId(), OrderView.class);
		assertThat(orderView.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
	}
```

Add imports: `import com.testingai.camunda.domain.OrderStatus;`, `import com.testingai.camunda.domain.OrderStep;`.

Note: these read `OrderReadModel` state, which the worker updates synchronously as part of handling the job (not waiting on process-instance completion assertions), so no `CamundaAssert` retry/polling is strictly needed here — but if the `GET` races the job worker's async processing and reads a stale `IN_PROGRESS` status, wrap the `GET`+assertion pair in a short retry loop (poll every 200ms up to ~3 seconds) rather than assuming zero latency between "order started" and "worker finished."

- [ ] **Step 2: Run tests to verify they fail, then pass**

Run: `mvn test -pl camunda/spring-demo -am -Dtest=DemoIntegrationTest`
Expected: PASS (all tests in this class — 5 total: low-value happy path from Task 3, high-value approve/reject from Task 4, these 2 failure-path tests).

- [ ] **Step 3: Commit**

```bash
git add workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/controller/DemoIntegrationTest.java
git commit -m "test(workflow-engines): add error-boundary failure path integration tests"
```

---

### Task 6: Gatling load test

**Files:**
- Create: `workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running app's `/demo/camunda/orders` HTTP endpoint (manual verification only — excluded from `mvn test`).

- [ ] **Step 1: Write the simulation**

```java
package com.testingai.camunda.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private static final String LOW_VALUE_ORDER_BODY = """
			{"customerId":"load-test","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":null}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8093")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("Camunda Demo")
			.exec(http("Start Order - Low Value (happy path)").post("/demo/camunda/orders")
					.body(StringBody(LOW_VALUE_ORDER_BODY)).check(status().is(200)));

	{
		// 2 users, ramped a few seconds apart, matching every other DemoSimulation's pacing in this repo. High-value
		// (approval-required) and failAt flows aren't load-tested — they need a human-shaped follow-up call mid-flight,
		// not a fire-and-forget request, so this simulation only covers the straight-line happy path.
		setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(90));
	}
}
```

- [ ] **Step 2: Verify it's excluded from `mvn test`**

Run: `mvn test -pl camunda/spring-demo -am`
Expected: `DemoSimulation` is NOT in the list of run tests.

- [ ] **Step 3: Commit**

```bash
git add workflow-engines/camunda/spring-demo/src/test/java/com/testingai/camunda/performance
git commit -m "test(workflow-engines): add Gatling load test for camunda-demo"
```

(Manual verification of this Gatling run against a live docker-compose-backed app happens in Task 8's final verification, once the compose stack exists.)

---

### Task 7: Docker Compose + cross-cutting fixes

**Files:**
- Create: `workflow-engines/camunda/docker/docker-compose.yml`
- Modify: `.githooks/pre-commit`
- Modify: `CLAUDE.md`

**Interfaces:** None — infrastructure and tooling only.

- [ ] **Step 1: Write the docker-compose file**

`workflow-engines/camunda/docker/docker-compose.yml`:

```yaml
name: camunda

services:
  camunda:
    image: camunda/camunda:8.8.0
    container_name: camunda
    ports:
      - "8080:8080"
      - "26500:26500"
    environment:
      CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI: "true"
      CAMUNDA_DATA_SECONDARY_STORAGE_TYPE: elasticsearch
      CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL: http://elasticsearch:9200
      CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_USERNAME: ""
      CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_PASSWORD: ""
    depends_on:
      - elasticsearch

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.17.4
    container_name: camunda-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
```

Note: `8.17.4` is confirmed working against `camunda/camunda:8.8.0` by an actual live `docker compose up` in this session — `8.15.0` was tried first and failed with a Jackson `HealthResponseBody`/`requireNonNull` deserialization error from Camunda's Elasticsearch client (a genuine version incompatibility, not a config issue), which went away entirely on `8.17.4`.

- [ ] **Step 2: Verify docker-compose starts (manual)**

```bash
cd workflow-engines/camunda/docker
docker compose up -d
sleep 40
curl -s http://localhost:8080/v2/topology
```

Expected: a JSON body with `"brokers":[{...,"partitions":[{"partitionId":1,"role":"leader","health":"healthy"}]...}]`. Note there is no `/actuator/health` endpoint on this unified image (unlike the Axon Server demo) — `/v2/topology` is the equivalent readiness signal; `/operate` and `/tasklist` (redirecting to their respective SPAs, HTTP 302) are also good manual liveness checks.

```bash
docker compose down
```

- [ ] **Step 3: Extend `.githooks/pre-commit`**

Find the line matching the staged-file grep pattern (currently includes `communication-protocols`, `reactive-programming`, etc. per prior work) and add `workflow-engines`:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines)/.*\.java$' || true)
```

Then find the block that runs `mvn spotless:apply` per-directory (e.g. the `communication-protocols` block) and add a matching one:

```bash
if echo "$STAGED_JAVA" | grep -q '^workflow-engines/'; then
    echo "[pre-commit] Applying Spotless formatting to staged workflow-engines Java files..."
    (cd "$ROOT/workflow-engines" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 4: Update `CLAUDE.md`**

Add a new command section, placed after "GraphQL communication protocol demo" (or wherever the last communication-protocols/reactive-programming section is) and before "Spring Boot starter demo":

```markdown
### Camunda workflow engine demo (run from the reactor root — requires Docker for both the app and `mvn test`)

```bash
docker compose -f workflow-engines/camunda/docker/docker-compose.yml up -d   # Camunda 8 (Zeebe/Operate/Tasklist) + Elasticsearch

cd workflow-engines

mvn clean package                                    # build (part of the reactor build)
mvn test -pl camunda/spring-demo                      # unit + integration tests — requires a working Docker daemon (Testcontainers), NOT the compose stack above
mvn test -pl camunda/spring-demo -Dtest=ClassName      # single test class
mvn -pl camunda/spring-demo spring-boot:run            # run the app (:8093) against the compose stack; Operate UI at http://localhost:8080
mvn gatling:test -pl camunda/spring-demo               # Gatling load test — requires the app running first
```
```

Also add a row to the repository layout table:

```markdown
| `workflow-engines/camunda/spring-demo/` | Camunda 8 (Zeebe) BPMN workflow demo — service tasks, exclusive gateway, user task (approval), and error-boundary-driven failure routing over the order-fulfillment domain shared with `distributed-transactions/saga`; requires Docker for both the running app (`docker compose`) and `mvn test` (Testcontainers) |
```

- [ ] **Step 5: Commit**

```bash
git add workflow-engines/camunda/docker .githooks/pre-commit CLAUDE.md
git commit -m "feat(workflow-engines): add Camunda docker-compose and wire up pre-commit/CLAUDE.md"
```

---

### Task 8: Documentation and final verification

**Files:**
- Create: `workflow-engines/README.md`
- Create: `workflow-engines/camunda/README.md`
- Create: `workflow-engines/camunda/spring-demo/README.md`

**Interfaces:** None — documentation and verification only.

- [ ] **Step 1: Write `workflow-engines/README.md`**

```markdown
# Workflow Engines — Demos

This directory contains runnable demos for workflow/BPMN orchestration engines, structured the same way as `../cqrs-event-sourcing/`: one infrastructure component and one Spring Boot demo app per engine.

| Engine | Infrastructure | Best fit |
|---|---|---|
| [Camunda 8](camunda/) | Camunda 8 self-managed (Zeebe + Operate + Tasklist) + Elasticsearch | Long-running, human-task-capable business processes modeled visually as BPMN diagrams; declarative branching and error routing instead of hand-written orchestration code |

More workflow engines may be added here over time (e.g. Temporal, Flowable), at which point this README will grow into a comparison guide like `../message-brokers/README.md`.
```

- [ ] **Step 2: Write `workflow-engines/camunda/README.md`**

```markdown
# Camunda 8 Demo

Demonstrates Camunda 8 (built on the Zeebe workflow engine) — a BPMN 2.0 process orchestration platform where the process is a visual diagram, not hand-written control-flow code — via one Spring Boot app (`spring-demo`) covering service tasks, an exclusive gateway, a user task, and error-boundary-driven failure routing.

This reuses the exact order-fulfillment domain from [`distributed-transactions/saga`](../../distributed-transactions/saga/) (reserve inventory → process payment → arrange shipping) so the two demos are directly comparable: saga hand-writes orchestration and compensation in Java; this demo expresses the same process as a `.bpmn` diagram, with Camunda's engine driving execution.

## The four patterns

| Pattern | BPMN element | What it demonstrates |
|---|---|---|
| Service tasks | `Reserve Inventory`, `Process Payment`, `Arrange Shipping`, `Release Inventory` | External worker processes (`@JobWorker`) polling/executing units of work — Camunda's equivalent of saga's hand-written participants, but declared in the process model rather than Java control flow |
| Exclusive gateway | `High-Value Order?` | Conditional branching expressed declaratively in the process diagram (a FEEL expression on the sequence flow) instead of an `if` statement buried in orchestrator code |
| User task | `Approve Order` | Human-in-the-loop step — something saga's code-only orchestrator has no first-class way to express; the process instance genuinely pauses until a human (or, in this demo, a REST call) completes it |
| Error boundary event | attached to `Reserve Inventory` / `Process Payment` | Declarative failure routing to a cleanup path, BPMN's alternative to saga's manually-written `compensate()` step-unwinding loop |

### Service tasks

**Pros**
- Each unit of work is an independently deployable/scalable worker process, polling for its job type
- Retries, timeouts, and backoff are engine-managed, not hand-rolled per participant
- The process diagram itself documents which steps exist and in what order

**Cons**
- Requires running a separate broker/engine — more moving parts than a single Java method call
- Debugging spans the process engine and the worker process, rather than a single stack trace

**Typical use cases**
- Any multi-step business process where each step could reasonably be its own service or team's responsibility
- Long-running processes that must survive a restart of the participating services

### Exclusive gateway

**Pros**
- Branching logic lives in the diagram, visible to non-developers reviewing the process
- FEEL expressions on sequence flows are simple, readable conditions — no hidden Java `if`/`else` to hunt for
- A `default` flow makes the "otherwise" case explicit and impossible to accidentally omit

**Cons**
- Complex branching logic (many conditions, nested decisions) can make a diagram harder to read than well-organized code
- Expression syntax (FEEL) is an additional thing to learn beyond the host language

**Typical use cases**
- Any point where the process should take a different path based on data already in its variables (order value, customer tier, risk score)

### User task

**Pros**
- First-class human-in-the-loop step — the process instance genuinely waits, with no polling loop to write
- Assignable to users/groups, queryable ("what's pending for me?"), and completable via a documented API
- Same instance keeps all the context (variables) gathered so far, available to whoever completes the task

**Cons**
- Introduces real wall-clock waiting into the process — needs monitoring for stuck/forgotten tasks
- Requires *some* task-consuming client (a real UI, or in this demo, a REST call) — the engine alone doesn't notify a human

**Typical use cases**
- Approvals, reviews, manual exception handling — anywhere a human decision gates progress

### Error boundary event

**Pros**
- Declarative failure routing: attach a boundary event to a task, point it at a cleanup path, done — no manual unwinding loop
- Different tasks can route to different (or shared) cleanup paths just by connecting the boundary event's outgoing flow
- The diagram shows the failure path as clearly as the happy path

**Cons**
- Only as good as the error codes the workers actually throw — a worker that throws a generic/unclassified exception produces an unhandled incident, not a routed error
- Complex compensation (undoing several already-completed steps in reverse order) needs either several boundary events wired carefully, or full BPMN Compensation Events (not used in this demo — see Scope)

**Typical use cases**
- Any step whose failure should trigger cleanup/rollback of what came before it, rather than just failing the whole process

## Running the demo

Requires Docker (Camunda 8 self-managed + Elasticsearch).

```bash
cd workflow-engines/camunda
docker compose -f docker/docker-compose.yml up -d
```

Wait ~40 seconds, then verify it's healthy (no `/actuator/health` on this unified image — `/v2/topology` is the equivalent readiness signal):

```bash
curl -s http://localhost:8080/v2/topology
```

Operate (visual process-instance monitoring — watch instances move through the diagram live as you exercise the API below) is at [http://localhost:8080/operate](http://localhost:8080/operate); Tasklist (browse/complete the `Approve Order` user task by hand instead of via `curl`) at [http://localhost:8080/tasklist](http://localhost:8080/tasklist).

```bash
cd spring-demo
mvn spring-boot:run
```

See [spring-demo/README.md](spring-demo/README.md) for `curl` walkthroughs of all four patterns.

## Scope

- Camunda 8 self-managed only, unauthenticated (`CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true`) — no Camunda 8 SaaS, no Identity/OIDC, matching this repo's "local demo, not production hardening" scope of every other module.
- Error boundary events only, not full BPMN Compensation Events (throw-compensation + `isForCompensation` associations) — this demo's cleanup path is a single shared "release inventory" service task reached via boundary events and a gateway branch, not a formal compensation subprocess.
- No Connectors, no DMN decision tables, no multi-instance/parallel gateways — one linear happy path plus the two branches described above, to keep the diagram legible.
- In-memory read model in the Spring app (`OrderReadModel`) for the order-status endpoint — no separate persistence layer.
- `mvn test` requires a working Docker daemon (Camunda's official Testcontainers-backed test library pulls and runs a real Camunda runtime per test class) — the one module in this repo where that's true.
```

- [ ] **Step 3: Write `workflow-engines/camunda/spring-demo/README.md`**

```markdown
# Camunda Demo (Spring Boot)

Spring Boot app driving the `order-fulfillment` BPMN process on Camunda 8, covering service tasks, an exclusive gateway, a user task, and error-boundary failure routing.

## Prerequisites

Java 21, Maven, Docker (for both the Camunda 8 compose stack below and, separately, for `mvn test`'s Testcontainers-backed integration tests).

## Run

```bash
cd workflow-engines/camunda
docker compose -f docker/docker-compose.yml up -d
cd spring-demo
mvn spring-boot:run
```

Operate: http://localhost:8080/operate — watch process instances move through the diagram live as you run the examples below.

## Walkthrough

**Low-value order — completes without approval:**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}]}'
# {"orderId":"...","processInstanceKey":...}

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"orderId":"...","processInstanceKey":...,"status":"FULFILLED","completedSteps":["RESERVE_INVENTORY","PROCESS_PAYMENT","ARRANGE_SHIPPING"]}
```

**High-value order — requires approval:**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":999.00}]}'
# {"orderId":"<orderId>",...} — status is PENDING_APPROVAL; check Tasklist (http://localhost:8080/tasklist) to see the pending "Approve Order" task, or:

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"status":"PENDING_APPROVAL", ...}

curl -s -X POST http://localhost:8093/demo/camunda/orders/<orderId>/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true}'

curl -s http://localhost:8093/demo/camunda/orders/<orderId>
# {"status":"FULFILLED", ...}
```

**Rejecting a high-value order:** same as above, but `{"approved":false}` — the order ends up `CANCELLED` after the `Release Inventory` cleanup step.

**Simulated failures (deterministic, via `failAt` — not random):**

```bash
curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":"RESERVE_INVENTORY"}'
# order ends up CANCELLED — the "Inventory Unavailable" error boundary event fires, routing straight to Order Cancelled (nothing to release, since reservation never succeeded)

curl -s http://localhost:8093/demo/camunda/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","items":[{"productId":"p1","quantity":1,"unitPrice":10.00}],"failAt":"PROCESS_PAYMENT"}'
# order ends up CANCELLED — the "Payment Declined" error boundary event fires, routing through Release Inventory (this time inventory *was* reserved) to Order Cancelled
```

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit + integration tests (Gatling excluded automatically) — requires a working Docker daemon
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # Gatling load test — requires the app AND the docker-compose stack running first
```

Unlike every other module in this repo, `mvn test` here needs Docker: Camunda's official `camunda-process-test-spring` library (`@CamundaSpringProcessTest`) spins up a real, ephemeral Camunda runtime per test class via Testcontainers. This is separate from (and doesn't need) the `docker compose` stack used to actually run the app — the compose stack is only for `mvn spring-boot:run` and the Gatling load test.

- **Gatling**: `com.testingai.camunda.performance.DemoSimulation` (`src/test/java/.../performance/`). Excluded from `mvn test` automatically; run with `mvn gatling:test` against a running app. HTML report under `target/gatling/`. Covers only the low-value happy-path flow — no JMeter counterpart for this module (a second tool duplicating one scenario isn't worth the maintenance here, unlike `communication-protocols`' dual-tool convention).
```

- [ ] **Step 4: Run Spotless formatting**

Run: `cd workflow-engines && mvn spotless:apply && cd ..`
Expected: `BUILD SUCCESS`; review `git diff` afterward for any auto-formatting changes and keep them.

- [ ] **Step 5: Full build and test verification**

```bash
cd workflow-engines
mvn clean package
mvn test -pl camunda/spring-demo
```

Expected: both `BUILD SUCCESS` (requires Docker running throughout).

- [ ] **Step 6: Manual smoke test**

```bash
docker compose -f camunda/docker/docker-compose.yml up -d
sleep 30
mvn -pl camunda/spring-demo spring-boot:run
```

In another terminal, run every `curl` example from `camunda/spring-demo/README.md` (Step 3 above) and confirm each returns the expected shape; open Operate (http://localhost:8080/operate) and confirm you can see the process instances you just created, including one paused at `Approve Order`. Run the Gatling simulation (`mvn gatling:test -pl camunda/spring-demo`) and confirm it completes with 0 failures. Stop the app (`Ctrl+C`) and the compose stack (`docker compose -f camunda/docker/docker-compose.yml down`) once confirmed.

- [ ] **Step 7: Commit**

```bash
git add workflow-engines/README.md workflow-engines/camunda/README.md workflow-engines/camunda/spring-demo/README.md
git commit -m "docs(workflow-engines): document the Camunda demo"
```
