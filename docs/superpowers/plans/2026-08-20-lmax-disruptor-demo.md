# LMAX Disruptor Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `concurrency-patterns/lmax-disruptor/spring-demo` Spring Boot module demonstrating six LMAX Disruptor patterns (single handler, parallel handlers, diamond dependency graph, producer-type comparison, wait-strategy comparison, exception handling) over a trading order-matching domain.

**Architecture:** One Spring Boot 3.4.4 / Java 21 app with a package-per-pattern layout (matching the repo's `message-brokers/`/`distributed-transactions/` convention), a single `DemoController` exposing one REST endpoint per pattern, four persistent Spring-managed `Disruptor` instances (started in `@PostConstruct`, shut down in `@PreDestroy`) and two patterns (`producer/`, `waitstrategy/`) that build/tear down ephemeral `Disruptor` instances per request because they compare configurations fixed at construction time.

**Tech Stack:** Spring Boot 3.4.4, Java 21, `com.lmax:disruptor` 4.0.0, JUnit 5 + MockMvc + AssertJ, Gatling (load test only).

**Spec:** `docs/superpowers/specs/2026-08-20-lmax-disruptor-demo-design.md`

## Global Constraints

- Java 21 (`maven.compiler.release`), Spring Boot 3.4.4 — matches every other module in this repo.
- No external infrastructure (no Docker) — module is fully in-process, like `distributed-transactions/`.
- `FailureSimulator` must use `FAILURE_RATE = 0.05` and `maybeThrow(String context)` — never a `shouldFail(): boolean` method (`.claude/rules/code-review.md`).
- Long-running Spring-managed `Disruptor` instances (`single/`, `parallel/`, `diamond/`, `errors/`) must be started in `@PostConstruct` and shut down in `@PreDestroy` — never left as a bare local (`.claude/rules/code-review.md`, the same rule applied to `ServiceBusProcessorClient`).
- All instance fields assigned once and never reassigned must be `private final`; fields assigned in `@PostConstruct`/`@PreDestroy` are exempt from `final` but must still be `private` (`.claude/rules/code-review.md`).
- Do not call `.toString()` explicitly when a value goes to an SLF4J `{}` placeholder or string concatenation (`.claude/rules/code-review.md`).
- Do not declare `throws InterruptedException` where the framework controls the call (e.g. inside a Disruptor `EventHandler`) — catch and restore the interrupt flag instead (`.claude/rules/code-review.md`).
- Prefer modern Java 21 idioms (records, pattern matching, text blocks, `SequencedCollection`) over pre-modern equivalents on any line you write (`.claude/rules/code-review.md`).
- Tests use plain JUnit 5 + `MockMvc`/`@WebMvcTest` with `@MockitoBean` (not the deprecated `@MockBean`) — no Spock/Groovy, matching `distributed-transactions/saga` (Spock has a known `@WebMvcTest` incompatibility in this repo).
- `eventCount` request parameter: default `1000`, hard cap `100000`, rejected with `400 Bad Request` above the cap.
- App port: `8100`.
- Package root: `com.testingai.disruptor`.

---

## Task 1: Reactor scaffold

**Files:**
- Create: `concurrency-patterns/pom.xml`
- Create: `concurrency-patterns/eclipse-formatter.xml`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/pom.xml`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/resources/application.yml`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/DisruptorDemoApplication.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/DisruptorDemoApplicationTest.java`
- Modify: `.githooks/pre-commit`

**Interfaces:**
- Produces: a buildable, empty Spring Boot app on port `8100`, plus a wired-up Spotless pre-commit hook for `concurrency-patterns/**/*.java`. Later tasks add packages under `com.testingai.disruptor`.

- [ ] **Step 1: Create the parent POM**

Create `concurrency-patterns/pom.xml`:

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
    <artifactId>concurrency-patterns</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Concurrency Patterns</name>
    <description>Parent POM for all concurrency-pattern demo modules</description>

    <modules>
        <module>lmax-disruptor/spring-demo</module>
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

```bash
cp distributed-transactions/eclipse-formatter.xml concurrency-patterns/eclipse-formatter.xml
```

- [ ] **Step 3: Create the module POM**

Create `concurrency-patterns/lmax-disruptor/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>concurrency-patterns</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>lmax-disruptor-demo</artifactId>
    <name>LMAX Disruptor Demo</name>
    <description>Learning and demonstration project for the LMAX Disruptor ring-buffer library over a trading order-matching domain</description>

    <properties>
        <disruptor.version>4.0.0</disruptor.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.lmax</groupId>
            <artifactId>disruptor</artifactId>
            <version>${disruptor.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.disruptor.DisruptorDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.disruptor.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create `application.yml`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8100
```

- [ ] **Step 5: Create the application class**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/DisruptorDemoApplication.java`:

```java
package com.testingai.disruptor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DisruptorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisruptorDemoApplication.class, args);
    }
}
```

- [ ] **Step 6: Write the context-loads test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/DisruptorDemoApplicationTest.java`:

```java
package com.testingai.disruptor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DisruptorDemoApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 7: Build and run the test**

```bash
cd concurrency-patterns/lmax-disruptor/spring-demo
mvn clean test
```

Expected: `BUILD SUCCESS`, one test run (`contextLoads`).

- [ ] **Step 8: Wire up the pre-commit Spotless hook**

Modify `.githooks/pre-commit` — extend the staged-file grep to include `concurrency-patterns`:

```bash
# old
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines|domain-driven-design)/.*\.java$' || true)
```
```bash
# new
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines|domain-driven-design|concurrency-patterns)/.*\.java$' || true)
```

And add a matching block after the `domain-driven-design` block (before the "Re-stage" comment):

```bash
if echo "$STAGED_JAVA" | grep -q '^concurrency-patterns/'; then
    echo "[pre-commit] Applying Spotless formatting to staged concurrency-patterns Java files..."
    (cd "$ROOT/concurrency-patterns" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 9: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add concurrency-patterns .githooks/pre-commit
git commit -m "feat(concurrency-patterns): scaffold lmax-disruptor-demo module"
```

---

## Task 2: Domain model

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Side.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Order.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/OrderEvent.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/OrderGenerator.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Fill.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/domain/OrderEventTest.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/domain/OrderGeneratorTest.java`

**Interfaces:**
- Consumes: nothing (base domain layer).
- Produces:
  - `enum Side { BUY, SELL }`
  - `record Order(String orderId, String symbol, Side side, int quantity, BigDecimal price)`
  - `class OrderEvent` — mutable ring-buffer payload; `void set(Order order)`; getters `getOrderId()`, `getSymbol()`, `getSide()`, `getQuantity()`, `getPrice()`, `getPublishNanos()`; `public static final EventTranslatorOneArg<OrderEvent, Order> TRANSLATOR`
  - `class OrderGenerator` — `static Order generate(long index)`
  - `record Fill(String symbol, String buyOrderId, String sellOrderId, int quantity, BigDecimal price)`

Every later task publishes onto a ring buffer via `ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i))` and reads events via `OrderEvent`'s getters.

- [ ] **Step 1: Write the failing `OrderEvent` test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/domain/OrderEventTest.java`:

```java
package com.testingai.disruptor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventTest {

    @Test
    void setCopiesAllFieldsFromTheOrderAndStampsAPublishTimestamp() {
        OrderEvent event = new OrderEvent();
        Order order = new Order("order-1", "AAPL", Side.BUY, 10, BigDecimal.TEN);

        long before = System.nanoTime();
        event.set(order);
        long after = System.nanoTime();

        assertThat(event.getOrderId()).isEqualTo("order-1");
        assertThat(event.getSymbol()).isEqualTo("AAPL");
        assertThat(event.getSide()).isEqualTo(Side.BUY);
        assertThat(event.getQuantity()).isEqualTo(10);
        assertThat(event.getPrice()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(event.getPublishNanos()).isBetween(before, after);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd concurrency-patterns/lmax-disruptor/spring-demo
mvn test -Dtest=OrderEventTest
```

Expected: compile error — `Side`, `Order`, `OrderEvent` do not exist yet.

- [ ] **Step 3: Implement `Side`, `Order`, and `OrderEvent`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Side.java`:

```java
package com.testingai.disruptor.domain;

public enum Side {
    BUY,
    SELL
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Order.java`:

```java
package com.testingai.disruptor.domain;

import java.math.BigDecimal;

public record Order(String orderId, String symbol, Side side, int quantity, BigDecimal price) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/OrderEvent.java`:

```java
package com.testingai.disruptor.domain;

import com.lmax.disruptor.EventTranslatorOneArg;

import java.math.BigDecimal;

public class OrderEvent {

    public static final EventTranslatorOneArg<OrderEvent, Order> TRANSLATOR = (event, sequence, order) -> event
            .set(order);

    private String orderId;
    private String symbol;
    private Side side;
    private int quantity;
    private BigDecimal price;
    private long publishNanos;

    public void set(Order order) {
        this.orderId = order.orderId();
        this.symbol = order.symbol();
        this.side = order.side();
        this.quantity = order.quantity();
        this.price = order.price();
        this.publishNanos = System.nanoTime();
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getPublishNanos() {
        return publishNanos;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=OrderEventTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Write the failing `OrderGenerator` test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/domain/OrderGeneratorTest.java`:

```java
package com.testingai.disruptor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderGeneratorTest {

    @Test
    void generatesDeterministicAlternatingBuySellOrders() {
        Order first = OrderGenerator.generate(0);
        Order second = OrderGenerator.generate(1);

        assertThat(first.side()).isEqualTo(Side.BUY);
        assertThat(second.side()).isEqualTo(Side.SELL);
        assertThat(first.orderId()).isEqualTo("order-0");
        assertThat(second.orderId()).isEqualTo("order-1");
    }

    @Test
    void generateIsPureAndRepeatable() {
        Order firstCall = OrderGenerator.generate(42);
        Order secondCall = OrderGenerator.generate(42);

        assertThat(firstCall).isEqualTo(secondCall);
    }
}
```

- [ ] **Step 6: Run it to verify it fails to compile**

```bash
mvn test -Dtest=OrderGeneratorTest
```

Expected: compile error — `OrderGenerator` does not exist yet.

- [ ] **Step 7: Implement `OrderGenerator` and `Fill`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/OrderGenerator.java`:

```java
package com.testingai.disruptor.domain;

import java.math.BigDecimal;
import java.util.List;

public final class OrderGenerator {

    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "GOOG");

    private OrderGenerator() {
    }

    public static Order generate(long index) {
        String symbol = SYMBOLS.get((int) (index % SYMBOLS.size()));
        Side side = index % 2 == 0 ? Side.BUY : Side.SELL;
        int quantity = 10 + (int) (index % 10);
        BigDecimal price = BigDecimal.valueOf(100 + (index % 5));
        return new Order("order-" + index, symbol, side, quantity, price);
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain/Fill.java`:

```java
package com.testingai.disruptor.domain;

import java.math.BigDecimal;

public record Fill(String symbol, String buyOrderId, String sellOrderId, int quantity, BigDecimal price) {
}
```

- [ ] **Step 8: Run it to verify it passes**

```bash
mvn test -Dtest=OrderGeneratorTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 9: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/domain \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/domain
git commit -m "feat(lmax-disruptor-demo): add order/event domain model"
```

---

## Task 3: Order matching engine

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/matching/RestingOrder.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/matching/OrderMatchingEngine.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/matching/OrderMatchingEngineTest.java`

**Interfaces:**
- Consumes: `Order`, `OrderEvent`, `Side`, `Fill` from `com.testingai.disruptor.domain` (Task 2).
- Produces: `class OrderMatchingEngine` — `List<Fill> match(OrderEvent incoming)`, `int restingOrderCount()`. Used by `diamond/`'s `MatchingHandler` (Task 6).

`RestingOrder` is mutable (quantity shrinks as it gets partially filled) and package-private — it never leaves `matching/`; the book must never store the shared, reused `OrderEvent` reference itself, since the ring buffer recycles that object.

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/matching/OrderMatchingEngineTest.java`:

```java
package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.Order;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.Side;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMatchingEngineTest {

    private final OrderMatchingEngine engine = new OrderMatchingEngine();

    private OrderEvent eventFor(Order order) {
        OrderEvent event = new OrderEvent();
        event.set(order);
        return event;
    }

    @Test
    void nonCrossingOrderRestsInTheBook() {
        Order sellOrder = new Order("sell-1", "AAPL", Side.SELL, 10, BigDecimal.valueOf(105));

        List<Fill> fills = engine.match(eventFor(sellOrder));

        assertThat(fills).isEmpty();
        assertThat(engine.restingOrderCount()).isEqualTo(1);
    }

    @Test
    void crossingOrderProducesAFill() {
        engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 10, BigDecimal.valueOf(100))));

        List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 10, BigDecimal.valueOf(101))));

        assertThat(fills).hasSize(1);
        Fill fill = fills.get(0);
        assertThat(fill.buyOrderId()).isEqualTo("buy-1");
        assertThat(fill.sellOrderId()).isEqualTo("sell-1");
        assertThat(fill.quantity()).isEqualTo(10);
        assertThat(fill.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(engine.restingOrderCount()).isZero();
    }

    @Test
    void partialFillLeavesRemainderResting() {
        engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 4, BigDecimal.valueOf(100))));

        List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 10, BigDecimal.valueOf(100))));

        assertThat(fills).hasSize(1);
        assertThat(fills.get(0).quantity()).isEqualTo(4);
        assertThat(engine.restingOrderCount()).isEqualTo(1);
    }

    @Test
    void ordersAtSamePriceMatchInTimePriorityOrder() {
        engine.match(eventFor(new Order("sell-1", "AAPL", Side.SELL, 5, BigDecimal.valueOf(100))));
        engine.match(eventFor(new Order("sell-2", "AAPL", Side.SELL, 5, BigDecimal.valueOf(100))));

        List<Fill> fills = engine.match(eventFor(new Order("buy-1", "AAPL", Side.BUY, 5, BigDecimal.valueOf(100))));

        assertThat(fills).hasSize(1);
        assertThat(fills.get(0).sellOrderId()).isEqualTo("sell-1");
        assertThat(engine.restingOrderCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=OrderMatchingEngineTest
```

Expected: compile error — `OrderMatchingEngine` does not exist yet.

- [ ] **Step 3: Implement `RestingOrder` and `OrderMatchingEngine`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/matching/RestingOrder.java`:

```java
package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Side;

import java.math.BigDecimal;

final class RestingOrder {

    private final String orderId;
    private final String symbol;
    private final Side side;
    private final BigDecimal price;
    private int quantity;

    RestingOrder(String orderId, String symbol, Side side, int quantity, BigDecimal price) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
    }

    String orderId() {
        return orderId;
    }

    int quantity() {
        return quantity;
    }

    void reduceQuantity(int filled) {
        this.quantity -= filled;
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/matching/OrderMatchingEngine.java`:

```java
package com.testingai.disruptor.matching;

import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.Side;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class OrderMatchingEngine {

    private final Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> bidBooks = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> askBooks = new ConcurrentHashMap<>();

    public List<Fill> match(OrderEvent incoming) {
        String orderId = incoming.getOrderId();
        String symbol = incoming.getSymbol();
        Side side = incoming.getSide();
        int remaining = incoming.getQuantity();
        BigDecimal price = incoming.getPrice();

        TreeMap<BigDecimal, Deque<RestingOrder>> oppositeBook = bookFor(side == Side.BUY ? askBooks : bidBooks,
                symbol, side == Side.BUY);

        List<Fill> fills = new ArrayList<>();
        while (remaining > 0 && !oppositeBook.isEmpty()) {
            Map.Entry<BigDecimal, Deque<RestingOrder>> best = oppositeBook.firstEntry();
            BigDecimal bestPrice = best.getKey();
            boolean crosses = side == Side.BUY ? price.compareTo(bestPrice) >= 0 : price.compareTo(bestPrice) <= 0;
            if (!crosses) {
                break;
            }

            Deque<RestingOrder> queue = best.getValue();
            RestingOrder resting = queue.peekFirst();
            int filledQuantity = Math.min(remaining, resting.quantity());

            fills.add(side == Side.BUY
                    ? new Fill(symbol, orderId, resting.orderId(), filledQuantity, bestPrice)
                    : new Fill(symbol, resting.orderId(), orderId, filledQuantity, bestPrice));

            remaining -= filledQuantity;
            resting.reduceQuantity(filledQuantity);
            if (resting.quantity() == 0) {
                queue.pollFirst();
                if (queue.isEmpty()) {
                    oppositeBook.remove(bestPrice);
                }
            }
        }

        if (remaining > 0) {
            TreeMap<BigDecimal, Deque<RestingOrder>> sameBook = bookFor(side == Side.BUY ? bidBooks : askBooks,
                    symbol, side != Side.BUY);
            sameBook.computeIfAbsent(price, p -> new LinkedList<>())
                    .addLast(new RestingOrder(orderId, symbol, side, remaining, price));
        }

        return fills;
    }

    public int restingOrderCount() {
        return countAll(bidBooks) + countAll(askBooks);
    }

    private TreeMap<BigDecimal, Deque<RestingOrder>> bookFor(Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> books,
            String symbol, boolean lowestFirst) {
        Comparator<BigDecimal> priceOrder = lowestFirst ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return books.computeIfAbsent(symbol, s -> new TreeMap<>(priceOrder));
    }

    private int countAll(Map<String, TreeMap<BigDecimal, Deque<RestingOrder>>> books) {
        int total = 0;
        for (TreeMap<BigDecimal, Deque<RestingOrder>> book : books.values()) {
            for (Deque<RestingOrder> queue : book.values()) {
                total += queue.size();
            }
        }
        return total;
    }
}
```

Note the asymmetry that keeps ask books lowest-price-first and bid books highest-price-first: `bookFor(askBooks, symbol, true)` (ascending — cheapest ask first) and `bookFor(bidBooks, symbol, false)` (descending — richest bid first). An incoming `BUY` reads `askBooks` ascending and, if unmatched, rests into `bidBooks` descending. An incoming `SELL` mirrors this.

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=OrderMatchingEngineTest
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/matching \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/matching
git commit -m "feat(lmax-disruptor-demo): add price-time priority order matching engine"
```

---

## Task 4: `single/` — one handler consuming the ring buffer

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/single/SingleHandlerResult.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/single/SingleHandlerService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/single/SingleHandlerServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent`, `OrderGenerator` from `com.testingai.disruptor.domain` (Task 2).
- Produces: `record SingleHandlerResult(long eventsProcessed, long elapsedMillis, double throughputPerSecond)`; `class SingleHandlerService` — `void start()` (`@PostConstruct`), `void shutdown()` (`@PreDestroy`), `SingleHandlerResult process(int eventCount)`. Consumed by `DemoController` (Task 10).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/single/SingleHandlerServiceTest.java`:

```java
package com.testingai.disruptor.single;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SingleHandlerServiceTest {

    private SingleHandlerService service;

    @BeforeEach
    void setUp() {
        service = new SingleHandlerService();
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void processesAllPublishedEvents() {
        SingleHandlerResult result = service.process(500);

        assertThat(result.eventsProcessed()).isEqualTo(500);
        assertThat(result.throughputPerSecond()).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=SingleHandlerServiceTest
```

Expected: compile error — `SingleHandlerResult`/`SingleHandlerService` do not exist yet.

- [ ] **Step 3: Implement `SingleHandlerResult` and `SingleHandlerService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/single/SingleHandlerResult.java`:

```java
package com.testingai.disruptor.single;

public record SingleHandlerResult(long eventsProcessed, long elapsedMillis, double throughputPerSecond) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/single/SingleHandlerService.java`:

```java
package com.testingai.disruptor.single;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SingleHandlerService {

    private static final int RING_BUFFER_SIZE = 2048;

    private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    @PostConstruct
    public void start() {
        disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptor.handleEventsWith(this::onEvent);
        ringBuffer = disruptor.start();
    }

    @PreDestroy
    public void shutdown() {
        disruptor.shutdown();
    }

    public SingleHandlerResult process(int eventCount) {
        CountDownLatch latch = new CountDownLatch(eventCount);
        latchRef.set(latch);

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
        }
        await(latch);
        long elapsed = System.currentTimeMillis() - start;

        return new SingleHandlerResult(eventCount, elapsed, throughputPerSecond(eventCount, elapsed));
    }

    private void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        CountDownLatch latch = latchRef.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private double throughputPerSecond(long events, long elapsedMillis) {
        if (elapsedMillis == 0) {
            return events;
        }
        return events * 1000.0 / elapsedMillis;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SingleHandlerServiceTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/single \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/single
git commit -m "feat(lmax-disruptor-demo): add single-handler Disruptor pattern"
```

---

## Task 5: `parallel/` — two independent handlers

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/ParallelResult.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/JournalHandler.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/RiskCheckHandler.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/ParallelHandlersService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/parallel/ParallelHandlersServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent`, `OrderGenerator` (Task 2).
- Produces: `record ParallelResult(long journalCount, long riskCheckCount, long elapsedMillis)`; `class ParallelHandlersService` — `void start()` (`@PostConstruct`), `void shutdown()` (`@PreDestroy`), `ParallelResult process(int eventCount)`. Consumed by `DemoController` (Task 10).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/parallel/ParallelHandlersServiceTest.java`:

```java
package com.testingai.disruptor.parallel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelHandlersServiceTest {

    private ParallelHandlersService service;

    @BeforeEach
    void setUp() {
        service = new ParallelHandlersService();
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void bothHandlersProcessEveryEventIndependently() {
        ParallelResult result = service.process(500);

        assertThat(result.journalCount()).isEqualTo(500);
        assertThat(result.riskCheckCount()).isEqualTo(500);
    }

    @Test
    void countersResetBetweenRuns() {
        service.process(500);
        ParallelResult second = service.process(200);

        assertThat(second.journalCount()).isEqualTo(200);
        assertThat(second.riskCheckCount()).isEqualTo(200);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=ParallelHandlersServiceTest
```

Expected: compile error — the `parallel` classes do not exist yet.

- [ ] **Step 3: Implement `ParallelResult`, `JournalHandler`, `RiskCheckHandler`, and `ParallelHandlersService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/ParallelResult.java`:

```java
package com.testingai.disruptor.parallel;

public record ParallelResult(long journalCount, long riskCheckCount, long elapsedMillis) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/JournalHandler.java`:

```java
package com.testingai.disruptor.parallel;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JournalHandler implements EventHandler<OrderEvent> {

    private final AtomicLong counter;
    private final AtomicReference<CountDownLatch> latchRef;

    public JournalHandler(AtomicLong counter, AtomicReference<CountDownLatch> latchRef) {
        this.counter = counter;
        this.latchRef = latchRef;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        counter.incrementAndGet();
        CountDownLatch latch = latchRef.get();
        if (latch != null) {
            latch.countDown();
        }
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/RiskCheckHandler.java`:

```java
package com.testingai.disruptor.parallel;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RiskCheckHandler implements EventHandler<OrderEvent> {

    private final AtomicLong counter;
    private final AtomicReference<CountDownLatch> latchRef;

    public RiskCheckHandler(AtomicLong counter, AtomicReference<CountDownLatch> latchRef) {
        this.counter = counter;
        this.latchRef = latchRef;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        counter.incrementAndGet();
        CountDownLatch latch = latchRef.get();
        if (latch != null) {
            latch.countDown();
        }
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel/ParallelHandlersService.java`:

```java
package com.testingai.disruptor.parallel;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ParallelHandlersService {

    private static final int RING_BUFFER_SIZE = 2048;

    private final AtomicLong journalCount = new AtomicLong();
    private final AtomicLong riskCheckCount = new AtomicLong();
    private final AtomicReference<CountDownLatch> journalLatchRef = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> riskCheckLatchRef = new AtomicReference<>();

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    @PostConstruct
    public void start() {
        disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptor.handleEventsWith(new JournalHandler(journalCount, journalLatchRef),
                new RiskCheckHandler(riskCheckCount, riskCheckLatchRef));
        ringBuffer = disruptor.start();
    }

    @PreDestroy
    public void shutdown() {
        disruptor.shutdown();
    }

    public ParallelResult process(int eventCount) {
        journalCount.set(0);
        riskCheckCount.set(0);
        journalLatchRef.set(new CountDownLatch(eventCount));
        riskCheckLatchRef.set(new CountDownLatch(eventCount));

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
        }
        await(journalLatchRef.get());
        await(riskCheckLatchRef.get());
        long elapsed = System.currentTimeMillis() - start;

        return new ParallelResult(journalCount.get(), riskCheckCount.get(), elapsed);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=ParallelHandlersServiceTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/parallel \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/parallel
git commit -m "feat(lmax-disruptor-demo): add parallel-handlers Disruptor pattern"
```

---

## Task 6: `diamond/` — dependency-graph handlers

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/DiamondResult.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/JournalHandler.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/ReplicationHandler.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/MatchingHandler.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/DiamondService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/diamond/DiamondServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent`, `OrderGenerator`, `Fill` (Task 2); `OrderMatchingEngine.match(OrderEvent): List<Fill>`, `.restingOrderCount(): int` (Task 3).
- Produces: `record DiamondResult(List<Fill> fills, int restingOrders, long elapsedMillis)`; `class DiamondService` — `void start()` (`@PostConstruct`), `void shutdown()` (`@PreDestroy`), `DiamondResult process(int eventCount)`. Consumed by `DemoController` (Task 10).

`diamond.JournalHandler`/`diamond.ReplicationHandler` are deliberately separate classes from `parallel.JournalHandler`/`parallel.RiskCheckHandler` (same shape as the repo's saga module: each flow package is self-contained, not sharing handler classes across packages).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/diamond/DiamondServiceTest.java`:

```java
package com.testingai.disruptor.diamond;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiamondServiceTest {

    private DiamondService service;

    @BeforeEach
    void setUp() {
        service = new DiamondService();
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void matchingHandlerRunsOnlyAfterBothUpstreamHandlersAndProducesFills() {
        DiamondResult result = service.process(500);

        long totalFilled = result.fills().stream().mapToLong(fill -> fill.quantity()).sum();
        assertThat(totalFilled).isGreaterThan(0);
        assertThat(result.restingOrders()).isGreaterThanOrEqualTo(0);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=DiamondServiceTest
```

Expected: compile error — the `diamond` classes do not exist yet.

- [ ] **Step 3: Implement `DiamondResult`, `JournalHandler`, `ReplicationHandler`, `MatchingHandler`, and `DiamondService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/DiamondResult.java`:

```java
package com.testingai.disruptor.diamond;

import com.testingai.disruptor.domain.Fill;

import java.util.List;

public record DiamondResult(List<Fill> fills, int restingOrders, long elapsedMillis) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/JournalHandler.java`:

```java
package com.testingai.disruptor.diamond;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.atomic.AtomicLong;

public class JournalHandler implements EventHandler<OrderEvent> {

    private final AtomicLong counter;

    public JournalHandler(AtomicLong counter) {
        this.counter = counter;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        counter.incrementAndGet();
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/ReplicationHandler.java`:

```java
package com.testingai.disruptor.diamond;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.OrderEvent;

import java.util.concurrent.atomic.AtomicLong;

public class ReplicationHandler implements EventHandler<OrderEvent> {

    private final AtomicLong counter;

    public ReplicationHandler(AtomicLong counter) {
        this.counter = counter;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        counter.incrementAndGet();
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/MatchingHandler.java`:

```java
package com.testingai.disruptor.diamond;

import com.lmax.disruptor.EventHandler;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.matching.OrderMatchingEngine;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class MatchingHandler implements EventHandler<OrderEvent> {

    private final OrderMatchingEngine matchingEngine;
    private final List<Fill> fills;
    private final AtomicReference<CountDownLatch> latchRef;

    public MatchingHandler(OrderMatchingEngine matchingEngine, List<Fill> fills,
            AtomicReference<CountDownLatch> latchRef) {
        this.matchingEngine = matchingEngine;
        this.fills = fills;
        this.latchRef = latchRef;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        fills.addAll(matchingEngine.match(event));
        CountDownLatch latch = latchRef.get();
        if (latch != null) {
            latch.countDown();
        }
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond/DiamondService.java`:

```java
package com.testingai.disruptor.diamond;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;
import com.testingai.disruptor.matching.OrderMatchingEngine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DiamondService {

    private static final int RING_BUFFER_SIZE = 2048;

    private final OrderMatchingEngine matchingEngine = new OrderMatchingEngine();
    private final List<Fill> fills = new CopyOnWriteArrayList<>();
    private final AtomicLong journalCount = new AtomicLong();
    private final AtomicLong replicationCount = new AtomicLong();
    private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    @PostConstruct
    public void start() {
        disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptor.handleEventsWith(new JournalHandler(journalCount), new ReplicationHandler(replicationCount))
                .then(new MatchingHandler(matchingEngine, fills, latchRef));
        ringBuffer = disruptor.start();
    }

    @PreDestroy
    public void shutdown() {
        disruptor.shutdown();
    }

    public DiamondResult process(int eventCount) {
        fills.clear();
        latchRef.set(new CountDownLatch(eventCount));

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
        }
        await(latchRef.get());
        long elapsed = System.currentTimeMillis() - start;

        return new DiamondResult(List.copyOf(fills), matchingEngine.restingOrderCount(), elapsed);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=DiamondServiceTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/diamond \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/diamond
git commit -m "feat(lmax-disruptor-demo): add diamond dependency-graph Disruptor pattern"
```

---

## Task 7: `producer/` — SINGLE vs. MULTI producer comparison

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/producer/ProducerStat.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/producer/ProducerComparisonService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/producer/ProducerComparisonServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent`, `OrderGenerator` (Task 2).
- Produces: `record ProducerStat(String producerType, int threadCount, long eventsProcessed, long elapsedMillis, double throughputPerSecond)`; `class ProducerComparisonService` — `List<ProducerStat> compare(int eventCount, int threadCount)` (plain `@Service`, no lifecycle methods — builds/tears down ephemeral `Disruptor` instances per call). Consumed by `DemoController` (Task 10).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/producer/ProducerComparisonServiceTest.java`:

```java
package com.testingai.disruptor.producer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerComparisonServiceTest {

    private final ProducerComparisonService service = new ProducerComparisonService();

    @Test
    void comparesSingleAndMultiProducerConfigurations() {
        List<ProducerStat> stats = service.compare(200, 4);

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).producerType()).isEqualTo("SINGLE");
        assertThat(stats.get(0).threadCount()).isEqualTo(1);
        assertThat(stats.get(0).eventsProcessed()).isEqualTo(200);
        assertThat(stats.get(1).producerType()).isEqualTo("MULTI");
        assertThat(stats.get(1).threadCount()).isEqualTo(4);
        assertThat(stats.get(1).eventsProcessed()).isEqualTo(200);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=ProducerComparisonServiceTest
```

Expected: compile error — `ProducerStat`/`ProducerComparisonService` do not exist yet.

- [ ] **Step 3: Implement `ProducerStat` and `ProducerComparisonService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/producer/ProducerStat.java`:

```java
package com.testingai.disruptor.producer;

public record ProducerStat(String producerType, int threadCount, long eventsProcessed, long elapsedMillis,
        double throughputPerSecond) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/producer/ProducerComparisonService.java`:

```java
package com.testingai.disruptor.producer;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProducerComparisonService {

    private static final int RING_BUFFER_SIZE = 2048;

    public List<ProducerStat> compare(int eventCount, int threadCount) {
        List<ProducerStat> stats = new ArrayList<>();
        stats.add(run(ProducerType.SINGLE, eventCount, 1));
        stats.add(run(ProducerType.MULTI, eventCount, Math.max(1, threadCount)));
        return stats;
    }

    private ProducerStat run(ProducerType producerType, int eventCount, int publisherThreads) {
        Disruptor<OrderEvent> disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE, producerType, new BlockingWaitStrategy());
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicLong processed = new AtomicLong();
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            processed.incrementAndGet();
            latch.countDown();
        });
        RingBuffer<OrderEvent> ringBuffer = disruptor.start();

        try {
            long start = System.currentTimeMillis();
            publish(ringBuffer, eventCount, publisherThreads);
            latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            return new ProducerStat(producerType.name(), publisherThreads, processed.get(), elapsed,
                    throughputPerSecond(processed.get(), elapsed));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for producer comparison run", e);
        } finally {
            disruptor.shutdown();
        }
    }

    private void publish(RingBuffer<OrderEvent> ringBuffer, int eventCount, int publisherThreads) {
        if (publisherThreads == 1) {
            for (long i = 0; i < eventCount; i++) {
                ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
            }
            return;
        }

        int perThread = eventCount / publisherThreads;
        int remainder = eventCount % publisherThreads;
        List<Runnable> tasks = new ArrayList<>();
        long nextIndex = 0;
        for (int t = 0; t < publisherThreads; t++) {
            long fromIndex = nextIndex;
            int count = perThread + (t < remainder ? 1 : 0);
            long toIndex = fromIndex + count;
            tasks.add(() -> {
                for (long i = fromIndex; i < toIndex; i++) {
                    ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
                }
            });
            nextIndex = toIndex;
        }

        ExecutorService executor = Executors.newFixedThreadPool(publisherThreads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(executor.submit(task));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Publisher task failed", e);
        } finally {
            executor.shutdown();
        }
    }

    private double throughputPerSecond(long events, long elapsedMillis) {
        if (elapsedMillis == 0) {
            return events;
        }
        return events * 1000.0 / elapsedMillis;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=ProducerComparisonServiceTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/producer \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/producer
git commit -m "feat(lmax-disruptor-demo): add producer-type comparison Disruptor pattern"
```

---

## Task 8: `waitstrategy/` — wait-strategy comparison

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/waitstrategy/WaitStrategyStat.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/waitstrategy/WaitStrategyComparisonService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/waitstrategy/WaitStrategyComparisonServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent` (incl. `getPublishNanos()`), `OrderGenerator` (Task 2).
- Produces: `record WaitStrategyStat(String strategyName, long eventsProcessed, long elapsedMillis, double throughputPerSecond, double avgLatencyMicros)`; `class WaitStrategyComparisonService` — `List<WaitStrategyStat> compare(int eventCount)` (plain `@Service`, ephemeral `Disruptor` per strategy per call). Consumed by `DemoController` (Task 10).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/waitstrategy/WaitStrategyComparisonServiceTest.java`:

```java
package com.testingai.disruptor.waitstrategy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitStrategyComparisonServiceTest {

    private final WaitStrategyComparisonService service = new WaitStrategyComparisonService();

    @Test
    void comparesAllThreeWaitStrategies() {
        List<WaitStrategyStat> stats = service.compare(200);

        assertThat(stats).hasSize(3);
        assertThat(stats).extracting(WaitStrategyStat::strategyName)
                .containsExactlyInAnyOrder("BLOCKING", "YIELDING", "BUSY_SPIN");
        assertThat(stats).allSatisfy(stat -> assertThat(stat.eventsProcessed()).isEqualTo(200));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=WaitStrategyComparisonServiceTest
```

Expected: compile error — `WaitStrategyStat`/`WaitStrategyComparisonService` do not exist yet.

- [ ] **Step 3: Implement `WaitStrategyStat` and `WaitStrategyComparisonService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/waitstrategy/WaitStrategyStat.java`:

```java
package com.testingai.disruptor.waitstrategy;

public record WaitStrategyStat(String strategyName, long eventsProcessed, long elapsedMillis,
        double throughputPerSecond, double avgLatencyMicros) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/waitstrategy/WaitStrategyComparisonService.java`:

```java
package com.testingai.disruptor.waitstrategy;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WaitStrategyComparisonService {

    private static final int RING_BUFFER_SIZE = 2048;

    public List<WaitStrategyStat> compare(int eventCount) {
        List<WaitStrategyStat> stats = new ArrayList<>();
        stats.add(run("BLOCKING", new BlockingWaitStrategy(), eventCount));
        stats.add(run("YIELDING", new YieldingWaitStrategy(), eventCount));
        stats.add(run("BUSY_SPIN", new BusySpinWaitStrategy(), eventCount));
        stats.sort((a, b) -> Double.compare(a.avgLatencyMicros(), b.avgLatencyMicros()));
        return stats;
    }

    private WaitStrategyStat run(String strategyName, WaitStrategy waitStrategy, int eventCount) {
        Disruptor<OrderEvent> disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE, ProducerType.SINGLE, waitStrategy);
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicLong processed = new AtomicLong();
        AtomicLong totalLatencyNanos = new AtomicLong();
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            totalLatencyNanos.addAndGet(System.nanoTime() - event.getPublishNanos());
            processed.incrementAndGet();
            latch.countDown();
        });
        RingBuffer<OrderEvent> ringBuffer = disruptor.start();

        try {
            long start = System.currentTimeMillis();
            for (long i = 0; i < eventCount; i++) {
                ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
            }
            latch.await(30, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            long processedCount = processed.get();
            double avgLatencyMicros = processedCount == 0 ? 0 : totalLatencyNanos.get() / 1000.0 / processedCount;
            return new WaitStrategyStat(strategyName, processedCount, elapsed,
                    throughputPerSecond(processedCount, elapsed), avgLatencyMicros);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for wait-strategy comparison run", e);
        } finally {
            disruptor.shutdown();
        }
    }

    private double throughputPerSecond(long events, long elapsedMillis) {
        if (elapsedMillis == 0) {
            return events;
        }
        return events * 1000.0 / elapsedMillis;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=WaitStrategyComparisonServiceTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/waitstrategy \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/waitstrategy
git commit -m "feat(lmax-disruptor-demo): add wait-strategy comparison Disruptor pattern"
```

---

## Task 9: `errors/` — exception handling with `FailureSimulator`

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/util/FailureSimulator.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/ErrorsResult.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/ErrorsService.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/errors/util/FailureSimulatorTest.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/errors/ErrorsServiceTest.java`

**Interfaces:**
- Consumes: `OrderEvent`, `OrderGenerator` (Task 2).
- Produces: `class FailureSimulator` — `static void maybeThrow(String context)` (`FAILURE_RATE = 0.05`); `record ErrorsResult(long succeeded, long failed, long elapsedMillis)`; `class ErrorsService` — `void start()` (`@PostConstruct`), `void shutdown()` (`@PreDestroy`), `ErrorsResult process(int eventCount)`. Consumed by `DemoController` (Task 10).

- [ ] **Step 1: Write the failing `FailureSimulator` test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/errors/util/FailureSimulatorTest.java`:

```java
package com.testingai.disruptor.errors.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSimulatorTest {

    @Test
    void maybeThrowFailsWithinExpectedRateBand() {
        int failures = 0;
        for (int i = 0; i < 1000; i++) {
            try {
                FailureSimulator.maybeThrow("test");
            } catch (RuntimeException ignored) {
                failures++;
            }
        }

        // With a 5% failure rate, expect roughly 50 failures; accept a 5-200 range to avoid flakiness.
        assertThat(failures).isBetween(5, 200);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=FailureSimulatorTest
```

Expected: compile error — `FailureSimulator` does not exist yet.

- [ ] **Step 3: Implement `FailureSimulator`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/util/FailureSimulator.java`:

```java
package com.testingai.disruptor.errors.util;

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

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=FailureSimulatorTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Write the failing `ErrorsService` test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/errors/ErrorsServiceTest.java`:

```java
package com.testingai.disruptor.errors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorsServiceTest {

    private ErrorsService service;

    @BeforeEach
    void setUp() {
        service = new ErrorsService();
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void ringBufferSurvivesSimulatedHandlerFailures() {
        ErrorsResult result = service.process(1000);

        assertThat(result.succeeded() + result.failed()).isEqualTo(1000);
        // With a 5% failure rate, expect roughly 50 failures; accept a wide band to avoid flakiness.
        assertThat(result.failed()).isBetween(5L, 200L);
    }
}
```

- [ ] **Step 6: Run it to verify it fails to compile**

```bash
mvn test -Dtest=ErrorsServiceTest
```

Expected: compile error — `ErrorsResult`/`ErrorsService` do not exist yet.

- [ ] **Step 7: Implement `ErrorsResult` and `ErrorsService`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/ErrorsResult.java`:

```java
package com.testingai.disruptor.errors;

public record ErrorsResult(long succeeded, long failed, long elapsedMillis) {
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors/ErrorsService.java`:

```java
package com.testingai.disruptor.errors;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.testingai.disruptor.domain.OrderEvent;
import com.testingai.disruptor.domain.OrderGenerator;
import com.testingai.disruptor.errors.util.FailureSimulator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ErrorsService {

    private static final Logger log = LoggerFactory.getLogger(ErrorsService.class);
    private static final int RING_BUFFER_SIZE = 2048;

    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    @PostConstruct
    public void start() {
        disruptor = new Disruptor<>(OrderEvent::new, RING_BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptor.handleEventsWith(this::onEvent);
        disruptor.setDefaultExceptionHandler(new CountingExceptionHandler());
        ringBuffer = disruptor.start();
    }

    @PreDestroy
    public void shutdown() {
        disruptor.shutdown();
    }

    public ErrorsResult process(int eventCount) {
        succeeded.set(0);
        failed.set(0);
        latchRef.set(new CountDownLatch(eventCount));

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(OrderEvent.TRANSLATOR, OrderGenerator.generate(i));
        }
        await(latchRef.get());
        long elapsed = System.currentTimeMillis() - start;

        return new ErrorsResult(succeeded.get(), failed.get(), elapsed);
    }

    private void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        FailureSimulator.maybeThrow("order-processing");
        succeeded.incrementAndGet();
        CountDownLatch latch = latchRef.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class CountingExceptionHandler implements ExceptionHandler<OrderEvent> {

        @Override
        public void handleEventException(Throwable ex, long sequence, OrderEvent event) {
            log.warn("Handler failed processing sequence {}: {}", sequence, ex.getMessage());
            failed.incrementAndGet();
            CountDownLatch latch = latchRef.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            log.error("Disruptor failed to start", ex);
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            log.error("Disruptor failed to shut down cleanly", ex);
        }
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

```bash
mvn test -Dtest=ErrorsServiceTest
```

Expected: `BUILD SUCCESS`, 1 test passed. (Note: this test has a small inherent flakiness risk since `FailureSimulator` is randomized — the 5-200 band over 1000 events keeps the expected failure rate of ~50 comfortably inside it.)

- [ ] **Step 9: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/errors \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/errors
git commit -m "feat(lmax-disruptor-demo): add exception-handling Disruptor pattern"
```

---

## Task 10: `controller/` — REST API surface

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller/DemoController.java`
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller/DemoExceptionHandler.java`
- Test: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `SingleHandlerService.process(int): SingleHandlerResult` (Task 4); `ParallelHandlersService.process(int): ParallelResult` (Task 5); `DiamondService.process(int): DiamondResult` (Task 6); `ProducerComparisonService.compare(int, int): List<ProducerStat>` (Task 7); `WaitStrategyComparisonService.compare(int): List<WaitStrategyStat>` (Task 8); `ErrorsService.process(int): ErrorsResult` (Task 9).
- Produces: six REST endpoints under `/demo/disruptor`, all consumed only by Gatling (Task 11) and manual `curl` walkthroughs (Task 12).

- [ ] **Step 1: Write the failing test**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/controller/DemoControllerTest.java`:

```java
package com.testingai.disruptor.controller;

import com.testingai.disruptor.diamond.DiamondResult;
import com.testingai.disruptor.diamond.DiamondService;
import com.testingai.disruptor.domain.Fill;
import com.testingai.disruptor.errors.ErrorsResult;
import com.testingai.disruptor.errors.ErrorsService;
import com.testingai.disruptor.parallel.ParallelHandlersService;
import com.testingai.disruptor.parallel.ParallelResult;
import com.testingai.disruptor.producer.ProducerComparisonService;
import com.testingai.disruptor.producer.ProducerStat;
import com.testingai.disruptor.single.SingleHandlerResult;
import com.testingai.disruptor.single.SingleHandlerService;
import com.testingai.disruptor.waitstrategy.WaitStrategyComparisonService;
import com.testingai.disruptor.waitstrategy.WaitStrategyStat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SingleHandlerService singleHandlerService;

    @MockitoBean
    private ParallelHandlersService parallelHandlersService;

    @MockitoBean
    private DiamondService diamondService;

    @MockitoBean
    private ProducerComparisonService producerComparisonService;

    @MockitoBean
    private WaitStrategyComparisonService waitStrategyComparisonService;

    @MockitoBean
    private ErrorsService errorsService;

    @Test
    void singleEndpointReturnsResult() throws Exception {
        when(singleHandlerService.process(anyInt())).thenReturn(new SingleHandlerResult(1000, 10, 100000.0));

        mockMvc.perform(post("/demo/disruptor/single").param("eventCount", "1000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsProcessed").value(1000));
    }

    @Test
    void parallelEndpointReturnsResult() throws Exception {
        when(parallelHandlersService.process(anyInt())).thenReturn(new ParallelResult(1000, 1000, 10));

        mockMvc.perform(post("/demo/disruptor/parallel").param("eventCount", "1000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.journalCount").value(1000))
                .andExpect(jsonPath("$.riskCheckCount").value(1000));
    }

    @Test
    void diamondEndpointReturnsResult() throws Exception {
        Fill fill = new Fill("AAPL", "order-1", "order-2", 10, BigDecimal.TEN);
        when(diamondService.process(anyInt())).thenReturn(new DiamondResult(List.of(fill), 3, 10));

        mockMvc.perform(post("/demo/disruptor/diamond").param("eventCount", "1000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fills[0].symbol").value("AAPL")).andExpect(jsonPath("$.restingOrders").value(3));
    }

    @Test
    void producerEndpointReturnsComparison() throws Exception {
        when(producerComparisonService.compare(anyInt(), anyInt())).thenReturn(List.of(
                new ProducerStat("SINGLE", 1, 1000, 10, 100000.0), new ProducerStat("MULTI", 4, 1000, 12, 83333.0)));

        mockMvc.perform(post("/demo/disruptor/producer").param("eventCount", "1000").param("threads", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].producerType").value("SINGLE"))
                .andExpect(jsonPath("$[1].producerType").value("MULTI"));
    }

    @Test
    void waitStrategyEndpointReturnsComparison() throws Exception {
        when(waitStrategyComparisonService.compare(anyInt()))
                .thenReturn(List.of(new WaitStrategyStat("BUSY_SPIN", 1000, 5, 200000.0, 2.0),
                        new WaitStrategyStat("YIELDING", 1000, 7, 142857.0, 5.0),
                        new WaitStrategyStat("BLOCKING", 1000, 10, 100000.0, 10.0)));

        mockMvc.perform(post("/demo/disruptor/waitstrategy").param("eventCount", "10000")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("BUSY_SPIN"));
    }

    @Test
    void errorsEndpointReturnsResult() throws Exception {
        when(errorsService.process(anyInt())).thenReturn(new ErrorsResult(950, 50, 10));

        mockMvc.perform(post("/demo/disruptor/errors").param("eventCount", "1000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(950)).andExpect(jsonPath("$.failed").value(50));
    }

    @Test
    void eventCountAboveCapIsRejected() throws Exception {
        mockMvc.perform(post("/demo/disruptor/single").param("eventCount", "100001"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=DemoControllerTest
```

Expected: compile error — `DemoController` does not exist yet.

- [ ] **Step 3: Implement `DemoController` and `DemoExceptionHandler`**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller/DemoController.java`:

```java
package com.testingai.disruptor.controller;

import com.testingai.disruptor.diamond.DiamondResult;
import com.testingai.disruptor.diamond.DiamondService;
import com.testingai.disruptor.errors.ErrorsResult;
import com.testingai.disruptor.errors.ErrorsService;
import com.testingai.disruptor.parallel.ParallelHandlersService;
import com.testingai.disruptor.parallel.ParallelResult;
import com.testingai.disruptor.producer.ProducerComparisonService;
import com.testingai.disruptor.producer.ProducerStat;
import com.testingai.disruptor.single.SingleHandlerResult;
import com.testingai.disruptor.single.SingleHandlerService;
import com.testingai.disruptor.waitstrategy.WaitStrategyComparisonService;
import com.testingai.disruptor.waitstrategy.WaitStrategyStat;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo/disruptor")
public class DemoController {

    static final int MAX_EVENT_COUNT = 100_000;

    private final SingleHandlerService singleHandlerService;
    private final ParallelHandlersService parallelHandlersService;
    private final DiamondService diamondService;
    private final ProducerComparisonService producerComparisonService;
    private final WaitStrategyComparisonService waitStrategyComparisonService;
    private final ErrorsService errorsService;

    public DemoController(SingleHandlerService singleHandlerService, ParallelHandlersService parallelHandlersService,
            DiamondService diamondService, ProducerComparisonService producerComparisonService,
            WaitStrategyComparisonService waitStrategyComparisonService, ErrorsService errorsService) {
        this.singleHandlerService = singleHandlerService;
        this.parallelHandlersService = parallelHandlersService;
        this.diamondService = diamondService;
        this.producerComparisonService = producerComparisonService;
        this.waitStrategyComparisonService = waitStrategyComparisonService;
        this.errorsService = errorsService;
    }

    @PostMapping("/single")
    public SingleHandlerResult single(@RequestParam(defaultValue = "1000") int eventCount) {
        return singleHandlerService.process(validate(eventCount));
    }

    @PostMapping("/parallel")
    public ParallelResult parallel(@RequestParam(defaultValue = "1000") int eventCount) {
        return parallelHandlersService.process(validate(eventCount));
    }

    @PostMapping("/diamond")
    public DiamondResult diamond(@RequestParam(defaultValue = "1000") int eventCount) {
        return diamondService.process(validate(eventCount));
    }

    @PostMapping("/producer")
    public List<ProducerStat> producer(@RequestParam(defaultValue = "1000") int eventCount,
            @RequestParam(defaultValue = "4") int threads) {
        return producerComparisonService.compare(validate(eventCount), threads);
    }

    @PostMapping("/waitstrategy")
    public List<WaitStrategyStat> waitStrategy(@RequestParam(defaultValue = "10000") int eventCount) {
        return waitStrategyComparisonService.compare(validate(eventCount));
    }

    @PostMapping("/errors")
    public ErrorsResult errors(@RequestParam(defaultValue = "1000") int eventCount) {
        return errorsService.process(validate(eventCount));
    }

    private int validate(int eventCount) {
        if (eventCount < 1 || eventCount > MAX_EVENT_COUNT) {
            throw new IllegalArgumentException(
                    "eventCount must be between 1 and " + MAX_EVENT_COUNT + ", got " + eventCount);
        }
        return eventCount;
    }
}
```

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller/DemoExceptionHandler.java`:

```java
package com.testingai.disruptor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=DemoControllerTest
```

Expected: `BUILD SUCCESS`, 7 tests passed.

- [ ] **Step 5: Run the full unit test suite**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all tests across every package pass.

- [ ] **Step 6: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller \
        concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/controller
git commit -m "feat(lmax-disruptor-demo): add REST controller wiring all six patterns"
```

---

## Task 11: Gatling load test

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the six `/demo/disruptor/*` endpoints (Task 10).
- Produces: nothing consumed by later tasks — this is the module's Gatling entry point, matching `<simulationClass>` already set in the module POM (Task 1) and excluded from `mvn test` by the inherited `**/performance/**` surefire exclude.

- [ ] **Step 1: Implement the simulation**

Create `concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/performance/DemoSimulation.java`:

```java
package com.testingai.disruptor.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8100")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder disruptorScenario = scenario("LMAX Disruptor Patterns")
            .exec(http("Single Handler").post("/demo/disruptor/single?eventCount=1000").check(status().is(200)))
            .exec(http("Parallel Handlers").post("/demo/disruptor/parallel?eventCount=1000").check(status().is(200)))
            .exec(http("Diamond Dependency Graph").post("/demo/disruptor/diamond?eventCount=1000")
                    .check(status().is(200)))
            .exec(http("Producer Comparison").post("/demo/disruptor/producer?eventCount=1000&threads=4")
                    .check(status().is(200)))
            .exec(http("Wait Strategy Comparison").post("/demo/disruptor/waitstrategy?eventCount=5000")
                    .check(status().is(200)))
            .exec(http("Simulated Handler Errors").post("/demo/disruptor/errors?eventCount=1000")
                    .check(status().is(200)));

    {
        setUp(disruptorScenario.injectOpen(atOnceUsers(5))).protocols(httpProtocol)
                .maxDuration(Duration.ofSeconds(60));
    }
}
```

- [ ] **Step 2: Verify `mvn test` still excludes it**

```bash
mvn test
```

Expected: `BUILD SUCCESS`; the test output must not mention `DemoSimulation` (the parent POM's `**/performance/**` surefire exclude keeps it out of the regular test run).

- [ ] **Step 3: Commit**

```bash
git add concurrency-patterns/lmax-disruptor/spring-demo/src/test/java/com/testingai/disruptor/performance
git commit -m "test(lmax-disruptor-demo): add Gatling load test covering all six endpoints"
```

---

## Task 12: Module and category READMEs

**Files:**
- Create: `concurrency-patterns/lmax-disruptor/spring-demo/README.md`
- Create: `concurrency-patterns/README.md`

**Interfaces:**
- Consumes: nothing — documentation only.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Write the module README**

Create `concurrency-patterns/lmax-disruptor/spring-demo/README.md`:

```markdown
# LMAX Disruptor Demo

Learning and demonstration project for the [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) — a lock-free, single-writer ring buffer for high-throughput, low-latency inter-thread messaging — applied to a small trading order-matching domain.

## Why no locks?

The ring buffer is a pre-allocated, fixed-size array of reusable `OrderEvent` objects. A single writer claims the next slot with an atomic increment (no locking); each consumer tracks its own cursor and only reads slots the producer has already published, coordinated through `Sequence` objects and a `SequenceBarrier` rather than a mutex. Because events are mutable and reused (not allocated per message), there is no garbage-collection pressure from the hot path either.

## Prerequisites

- Java 21
- Maven

No Docker/external infrastructure required — everything runs in-process.

## Running

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8100`. Swagger UI: `http://localhost:8100/swagger-ui/index.html`.

## Patterns demonstrated

| Pattern | Endpoint | What it shows |
|---|---|---|
| Single handler | `POST /demo/disruptor/single` | The minimal Disruptor setup: one `EventHandler` consuming the ring buffer sequentially. |
| Parallel handlers | `POST /demo/disruptor/parallel` | Two independent handlers (journal, risk-check) processing every event concurrently — `handleEventsWith(a, b)`. |
| Diamond dependency graph | `POST /demo/disruptor/diamond` | The classic LMAX pattern: journal + replication run in parallel, then a matching-engine handler runs only after both finish — `handleEventsWith(a, b).then(c)`. |
| Producer comparison | `POST /demo/disruptor/producer` | `ProducerType.SINGLE` vs. `ProducerType.MULTI`, compared under concurrent publishers. |
| Wait-strategy comparison | `POST /demo/disruptor/waitstrategy` | `BlockingWaitStrategy` vs. `YieldingWaitStrategy` vs. `BusySpinWaitStrategy`, compared for throughput/latency. |
| Exception handling | `POST /demo/disruptor/errors` | A custom `ExceptionHandler` + a `FailureSimulator` (5% rate) show the ring buffer surviving a handler exception without stopping. |

### Diamond dependency graph

```
                 ┌──────────────┐
       ┌────────▶│   Journal    │──────┐
       │         └──────────────┘      │
Order ─┤                                ├──▶ MatchingHandler ──▶ Fill(s)
       │         ┌──────────────┐      │
       └────────▶│ Replication  │──────┘
                 └──────────────┘
```

`MatchingHandler` only processes an event once both `Journal` and `Replication` have processed it — enforced by the Disruptor's sequence barrier, not application code.

## Walkthrough

All endpoints accept an optional `eventCount` query parameter (default `1000`, capped at `100000`).

```bash
# Single handler
curl -X POST "http://localhost:8100/demo/disruptor/single?eventCount=2000"

# Parallel handlers
curl -X POST "http://localhost:8100/demo/disruptor/parallel?eventCount=2000"

# Diamond dependency graph — returns fills produced by the matching engine
curl -X POST "http://localhost:8100/demo/disruptor/diamond?eventCount=2000"

# SINGLE vs MULTI producer comparison
curl -X POST "http://localhost:8100/demo/disruptor/producer?eventCount=2000&threads=4"

# Wait-strategy comparison — expect BUSY_SPIN lowest latency/highest CPU,
# BLOCKING highest latency/lowest CPU; results are hardware-dependent
curl -X POST "http://localhost:8100/demo/disruptor/waitstrategy?eventCount=5000"

# Exception handling — ~5% of events simulate a handler failure;
# succeeded + failed always equals eventCount
curl -X POST "http://localhost:8100/demo/disruptor/errors?eventCount=2000"
```

## Testing

```bash
mvn test                # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName
mvn gatling:test         # load test — requires the app running first (mvn spring-boot:run)
```

## Scope limits

- No persistence — the order book and all counters are in-memory only, reset on restart.
- `OrderMatchingEngine` is intentionally minimal: no order cancellation, no partial-fill edge cases beyond basic quantity decrementing.
- `errors/` demonstrates survival and observability of a handler failure, not recovery/replay of the failed event.
- `producer/`'s `SINGLE` run always publishes from exactly one thread, even if a higher `threads` value is requested — concurrent publishing to a `SINGLE`-type ring buffer is undefined behavior.
```

- [ ] **Step 2: Write the category README**

Create `concurrency-patterns/README.md`:

```markdown
# Concurrency Patterns — Demos

This directory contains runnable demos for concurrency primitives and patterns, structured the same way as `../distributed-transactions/`: one Spring Boot demo app per pattern/library, no external infrastructure required.

| Pattern | Demo | Best fit |
|---|---|---|
| [LMAX Disruptor](lmax-disruptor/) | Single handler, parallel handlers, diamond dependency graph, producer/wait-strategy comparisons, exception handling — over a trading order-matching domain | High-throughput, low-latency in-process event processing without lock contention or GC pressure from message allocation |

More concurrency patterns may be added here over time.
```

- [ ] **Step 3: Commit**

```bash
git add concurrency-patterns/README.md concurrency-patterns/lmax-disruptor/spring-demo/README.md
git commit -m "docs(lmax-disruptor-demo): add module and category READMEs"
```

---

## Task 13: Cross-cutting repo documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing — documentation only.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Add the command section to `CLAUDE.md`**

Modify `CLAUDE.md` — insert a new section immediately after the "### Saga pattern demo" section (before "### DDD banking ledger demo"):

```markdown
### LMAX Disruptor demo (run from the module root, no docker infrastructure required)

```bash
cd concurrency-patterns/lmax-disruptor/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```
```

- [ ] **Step 2: Add the repository layout row to `CLAUDE.md`**

Modify `CLAUDE.md` — in the "### Repository layout" table, add a row after the `distributed-transactions/*` row and before the `domain-driven-design/banking/spring-demo/` row:

```markdown
| `concurrency-patterns/lmax-disruptor/spring-demo/` | LMAX Disruptor ring-buffer library demo — single handler, parallel handlers, diamond dependency graph (the classic journal+replicate-then-business-logic pattern), producer-type and wait-strategy comparisons, and exception handling, over a trading order-matching domain; no external infrastructure required |
```

- [ ] **Step 3: Add the row to the root `README.md`**

Modify `README.md` — in the "## Repository layout" table, add a row after the `distributed-transactions/` row and before the `domain-driven-design/` row:

```markdown
| `concurrency-patterns/` | LMAX Disruptor ring-buffer concurrency library (single handler, parallel handlers, diamond dependency graph, producer/wait-strategy comparisons, exception handling) |
```

- [ ] **Step 4: Verify the docs render sensibly**

```bash
git diff CLAUDE.md README.md
```

Expected: two clean, additive diffs — a new command section + one new table row in `CLAUDE.md`, one new table row in `README.md`. No other lines touched.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document the concurrency-patterns/lmax-disruptor-demo module"
```

---

## Task 14: Final verification

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full clean build**

```bash
cd concurrency-patterns/lmax-disruptor/spring-demo
mvn clean package
```

Expected: `BUILD SUCCESS`, jar produced under `target/`.

- [ ] **Step 2: Full unit test run**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, every test in every package (`domain`, `matching`, `single`, `parallel`, `diamond`, `producer`, `waitstrategy`, `errors`, `errors.util`, `controller`) passes; `performance/DemoSimulation` does not run.

- [ ] **Step 3: Smoke-test the running app**

```bash
mvn spring-boot:run &
sleep 8
curl -s -X POST "http://localhost:8100/demo/disruptor/single?eventCount=1000" | head -c 300
echo
curl -s -X POST "http://localhost:8100/demo/disruptor/diamond?eventCount=1000" | head -c 300
echo
curl -s -X POST "http://localhost:8100/demo/disruptor/errors?eventCount=1000" | head -c 300
echo
kill %1
```

Expected: each `curl` returns a `200`-shaped JSON body (`SingleHandlerResult`, `DiamondResult`, `ErrorsResult` fields respectively) — confirms the persistent `@PostConstruct`-started Disruptors actually process requests end-to-end, not just in unit tests.

- [ ] **Step 4: Confirm the pre-commit hook covers the new module**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git log --oneline -1 -- concurrency-patterns/lmax-disruptor/spring-demo/src/main/java/com/testingai/disruptor/controller/DemoController.java
```

Expected: shows the Task 10 commit — confirms the file was committed cleanly through the hook (no formatting-related commit failures were silently ignored earlier in the plan).

- [ ] **Step 5: Final status check**

```bash
git status
```

Expected: clean working tree — every file created during this plan has been committed.

---
