# DDD Banking Ledger Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `domain-driven-design/banking/spring-demo`, a self-contained Spring Boot app demonstrating tactical DDD building blocks — aggregates with enforced invariants, value objects, domain events, repositories-as-ports, a cross-aggregate domain service, and two bounded contexts (`ledger`, `statements`) connected through an anti-corruption layer.

**Architecture:** Hexagonal / ports & adapters. `ledger` and `statements` each have `domain` (framework-free), `application` (use cases, ledger only), `infrastructure` (JPA adapters), and `web` (REST controllers) packages. `statements` never imports `ledger`'s domain types except inside its one `infrastructure.acl.LedgerEventTranslator`, which is the anti-corruption layer. Domain events cross the boundary via Spring's synchronous `ApplicationEventPublisher`/`@EventListener`.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring Data JPA, H2 (runtime, no docker), Lombok (infrastructure classes only), JUnit 5 + AssertJ + MockMvc, Gatling.

## Global Constraints

- Java 21 language level (`maven.compiler.release=21`); use records, sealed interfaces, and pattern-matching switches where the design calls for them.
- Module lives at `domain-driven-design/banking/spring-demo`, package root `com.testingai.banking`, app port `8099`.
- No Docker, no external infrastructure — H2 only, matching `distributed-transactions/saga`.
- Domain packages (`ledger.domain`, `statements.domain`) must have zero Spring/JPA imports. Only `infrastructure` and `web` packages may import Spring/JPA types.
- `statements` must never import `com.testingai.banking.ledger.domain.*` types anywhere except `statements.infrastructure.acl.LedgerEventTranslator`.
- Plain JUnit5/Spring test style (no Spock) — this repo's Spock+`@WebMvcTest` incompatibility does not apply here since we're not using Spock, but stick to `@SpringBootTest`/`MockMvc` throughout for consistency.
- No `util/FailureSimulator` — failures are the domain invariants themselves (`InsufficientFundsException`, `InvalidAmountException`, `CurrencyMismatchException`), not random injection.
- Field modifiers: constructor/field-assigned-once fields are `private final` everywhere except JPA entity fields (Hibernate needs mutable, non-final fields) and Gatling `Simulation` fields (which must still be `private final` per `.claude/rules/code-review.md`).
- No unnecessary `.toString()` calls on values passed to SLF4J placeholders, string concatenation, etc.
- `TransferMoneyUseCase`'s returned `transferId` is a use-case-level correlation id for the API response only — it is **not** embedded in `LedgerEvent`s or `StatementLine`s. A transfer produces two independent, ordinary ledger events (a `MoneyWithdrawn` on the source, a `MoneyDeposited` on the target), each translated into its own statement line exactly like a standalone deposit/withdrawal. (This resolves an ambiguity in the approved design doc's ledger-context section, which described tagging events with a shared transfer id; the event/statement-line record shapes in the same doc never carried such a field. Two independent events is the simpler, consistent behavior and is what every task below implements.)

---

### Task 1: Module scaffolding

**Files:**
- Create: `domain-driven-design/pom.xml`
- Create: `domain-driven-design/eclipse-formatter.xml` (copy of `distributed-transactions/eclipse-formatter.xml`)
- Create: `domain-driven-design/banking/spring-demo/pom.xml`
- Create: `domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/BankingDemoApplication.java`
- Create: `domain-driven-design/banking/spring-demo/src/main/resources/application.yml`
- Create: `domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/BankingDemoApplicationTest.java`
- Create: `domain-driven-design/banking/spring-demo/src/test/resources/application.yml`
- Modify: `.githooks/pre-commit`

**Interfaces:**
- Produces: a buildable, runnable Spring Boot app on port `8099` with H2/JPA wired up, that every later task adds classes into.

- [ ] **Step 1: Create the parent POM**

`domain-driven-design/pom.xml`:

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
    <artifactId>domain-driven-design</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Domain-Driven Design</name>
    <description>Parent POM for all domain-driven-design pattern demo modules</description>

    <modules>
        <module>banking/spring-demo</module>
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
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

- [ ] **Step 2: Copy the shared formatter config**

```bash
cp distributed-transactions/eclipse-formatter.xml domain-driven-design/eclipse-formatter.xml
```

- [ ] **Step 3: Create the module POM**

`domain-driven-design/banking/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>domain-driven-design</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>banking-demo</artifactId>
    <name>DDD Banking Ledger Demo</name>
    <description>Learning and demonstration project for tactical Domain-Driven Design patterns (aggregates, value objects, domain events, bounded contexts, anti-corruption layer)</description>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.banking.BankingDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.banking.performance.BankingSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create the application class**

`domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/BankingDemoApplication.java`:

```java
package com.testingai.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BankingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingDemoApplication.class, args);
    }
}
```

- [ ] **Step 5: Create the main and test application.yml**

`domain-driven-design/banking/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8099

spring:
  datasource:
    url: jdbc:h2:mem:banking;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.H2Dialect
```

`domain-driven-design/banking/spring-demo/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:banking-test;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
```

- [ ] **Step 6: Write the context-loads test**

`domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/BankingDemoApplicationTest.java`:

```java
package com.testingai.banking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BankingDemoApplicationTest {

    @Test
    void contextLoads() {}
}
```

- [ ] **Step 7: Extend the pre-commit hook**

In `.githooks/pre-commit`, extend the grep pattern to include the new category:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines|domain-driven-design)/.*\.java$' || true)
```

And add a matching block (after the `workflow-engines` block, before the "Re-stage" comment):

```bash
if echo "$STAGED_JAVA" | grep -q '^domain-driven-design/'; then
    echo "[pre-commit] Applying Spotless formatting to staged domain-driven-design Java files..."
    (cd "$ROOT/domain-driven-design" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 8: Build and run to verify scaffolding**

Run: `cd domain-driven-design && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package`
Expected: `BUILD SUCCESS`, `BankingDemoApplicationTest` passes.

Run: `mvn -pl banking/spring-demo spring-boot:run` (from `domain-driven-design/`), then in another terminal `curl -i http://localhost:8099/actuator 2>/dev/null; curl -i http://localhost:8099/swagger-ui/index.html`
Expected: app starts on `8099` without errors (a 404 on unmapped paths is fine — there are no endpoints yet). Stop the app afterward.

- [ ] **Step 9: Commit**

```bash
git add domain-driven-design/pom.xml domain-driven-design/eclipse-formatter.xml \
  domain-driven-design/banking/spring-demo/pom.xml \
  domain-driven-design/banking/spring-demo/src \
  .githooks/pre-commit
git commit -m "feat(domain-driven-design): scaffold the banking ledger demo module"
```

---

### Task 2: Domain exceptions + `Money` value object

**Files:**
- Create: `domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/exception/DomainException.java`
- Create: `.../ledger/domain/exception/InsufficientFundsException.java`
- Create: `.../ledger/domain/exception/InvalidAmountException.java`
- Create: `.../ledger/domain/exception/CurrencyMismatchException.java`
- Create: `.../ledger/domain/exception/AccountNotFoundException.java`
- Create: `.../ledger/domain/Money.java`
- Test: `domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/MoneyTest.java`

**Interfaces:**
- Produces: `Money(BigDecimal amount, Currency currency)` record with `Money.of(BigDecimal, String currencyCode)`, `.plus(Money)`, `.minus(Money)`, `.isNegative()`, `.isLessThan(Money)`; `DomainException` and its four subtypes, all in `ledger.domain.exception`.

- [ ] **Step 1: Write the failing test**

`MoneyTest.java`:

```java
package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void plusAddsAmountsOfSameCurrency() {
        Money five = Money.of(new BigDecimal("5.00"), "USD");
        Money three = Money.of(new BigDecimal("3.00"), "USD");

        assertThat(five.plus(three).amount()).isEqualByComparingTo("8.00");
    }

    @Test
    void minusSubtractsAmountsOfSameCurrency() {
        Money five = Money.of(new BigDecimal("5.00"), "USD");
        Money three = Money.of(new BigDecimal("3.00"), "USD");

        assertThat(five.minus(three).amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void plusRejectsMismatchedCurrencies() {
        Money usd = Money.of(new BigDecimal("5.00"), "USD");
        Money eur = Money.of(new BigDecimal("5.00"), "EUR");

        assertThatThrownBy(() -> usd.plus(eur)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void isLessThanComparesSameCurrencyAmounts() {
        Money five = Money.of(new BigDecimal("5.00"), "USD");
        Money three = Money.of(new BigDecimal("3.00"), "USD");

        assertThat(three.isLessThan(five)).isTrue();
        assertThat(five.isLessThan(three)).isFalse();
    }

    @Test
    void isNegativeDetectsNegativeAmount() {
        assertThat(Money.of(new BigDecimal("-1.00"), "USD").isNegative()).isTrue();
        assertThat(Money.of(new BigDecimal("1.00"), "USD").isNegative()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=MoneyTest` (from `domain-driven-design/`)
Expected: FAIL — `Money` does not exist.

- [ ] **Step 3: Write the exception hierarchy and `Money`**

`ledger/domain/exception/DomainException.java`:

```java
package com.testingai.banking.ledger.domain.exception;

public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
```

`ledger/domain/exception/InsufficientFundsException.java`:

```java
package com.testingai.banking.ledger.domain.exception;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
```

`ledger/domain/exception/InvalidAmountException.java`:

```java
package com.testingai.banking.ledger.domain.exception;

public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
```

`ledger/domain/exception/CurrencyMismatchException.java`:

```java
package com.testingai.banking.ledger.domain.exception;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(String message) {
        super(message);
    }
}
```

`ledger/domain/exception/AccountNotFoundException.java`:

```java
package com.testingai.banking.ledger.domain.exception;

public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
```

`ledger/domain/Money.java`:

```java
package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(
                    "Cannot combine %s and %s"
                            .formatted(this.currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=MoneyTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/exception \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/Money.java \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/MoneyTest.java
git commit -m "feat(domain-driven-design): add Money value object and domain exception hierarchy"
```

---

### Task 3: `AccountId`, `AggregateRoot`, and the `LedgerEvent` sealed hierarchy

**Files:**
- Create: `.../ledger/domain/AccountId.java`
- Create: `.../ledger/domain/AggregateRoot.java`
- Create: `.../ledger/domain/event/LedgerEvent.java`
- Create: `.../ledger/domain/event/AccountOpened.java`
- Create: `.../ledger/domain/event/MoneyDeposited.java`
- Create: `.../ledger/domain/event/MoneyWithdrawn.java`
- Test: `.../ledger/domain/AccountIdTest.java`
- Test: `.../ledger/domain/AggregateRootTest.java`

**Interfaces:**
- Consumes: `Money` (Task 2).
- Produces: `AccountId(UUID value)` with `AccountId.newId()`; `AggregateRoot` with `protected void registerEvent(LedgerEvent)` and `public List<LedgerEvent> pullDomainEvents()`; sealed `LedgerEvent` permitting `AccountOpened`, `MoneyDeposited`, `MoneyWithdrawn` — each a record with an `accountId()` and `occurredAt()` accessor.

- [ ] **Step 1: Write the failing tests**

`AccountIdTest.java`:

```java
package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdTest {

    @Test
    void newIdGeneratesUniqueValues() {
        assertThat(AccountId.newId()).isNotEqualTo(AccountId.newId());
    }

    @Test
    void wrapsGivenUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(new AccountId(uuid).value()).isEqualTo(uuid);
    }
}
```

`AggregateRootTest.java`:

```java
package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.event.LedgerEvent;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    private static class TestAggregate extends AggregateRoot {
        void raise(LedgerEvent event) {
            registerEvent(event);
        }
    }

    @Test
    void pullDomainEventsReturnsAndClearsPendingEvents() {
        TestAggregate aggregate = new TestAggregate();
        AccountId accountId = AccountId.newId();
        MoneyDeposited event =
                new MoneyDeposited(accountId, Money.of(new BigDecimal("10.00"), "USD"), Instant.now());

        aggregate.raise(event);
        var pulled = aggregate.pullDomainEvents();

        assertThat(pulled).containsExactly(event);
        assertThat(aggregate.pullDomainEvents()).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountIdTest,AggregateRootTest`
Expected: FAIL to compile — `AccountId`, `AggregateRoot`, `MoneyDeposited` don't exist yet.

- [ ] **Step 3: Write the sealed event hierarchy**

`ledger/domain/event/LedgerEvent.java`:

```java
package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import java.time.Instant;

public sealed interface LedgerEvent permits AccountOpened, MoneyDeposited, MoneyWithdrawn {
    AccountId accountId();

    Instant occurredAt();
}
```

`ledger/domain/event/AccountOpened.java`:

```java
package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.time.Instant;

public record AccountOpened(AccountId accountId, String ownerName, Money openingBalance, Instant occurredAt)
        implements LedgerEvent {}
```

`ledger/domain/event/MoneyDeposited.java`:

```java
package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.time.Instant;

public record MoneyDeposited(AccountId accountId, Money amount, Instant occurredAt) implements LedgerEvent {}
```

`ledger/domain/event/MoneyWithdrawn.java`:

```java
package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.time.Instant;

public record MoneyWithdrawn(AccountId accountId, Money amount, Instant occurredAt) implements LedgerEvent {}
```

- [ ] **Step 4: Write `AccountId` and `AggregateRoot`**

`ledger/domain/AccountId.java`:

```java
package com.testingai.banking.ledger.domain;

import java.util.UUID;

public record AccountId(UUID value) {

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }
}
```

`ledger/domain/AggregateRoot.java`:

```java
package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.event.LedgerEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {

    private final List<LedgerEvent> pendingEvents = new ArrayList<>();

    protected void registerEvent(LedgerEvent event) {
        pendingEvents.add(event);
    }

    public List<LedgerEvent> pullDomainEvents() {
        List<LedgerEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountIdTest,AggregateRootTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/AccountId.java \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/AggregateRoot.java \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/event \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/AccountIdTest.java \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/AggregateRootTest.java
git commit -m "feat(domain-driven-design): add AccountId, AggregateRoot, and the LedgerEvent hierarchy"
```

---

### Task 4: `Account` aggregate root

**Files:**
- Create: `.../ledger/domain/Account.java`
- Test: `.../ledger/domain/AccountTest.java`

**Interfaces:**
- Consumes: `Money`, `AccountId`, `AggregateRoot`, `LedgerEvent` subtypes, `InvalidAmountException`, `InsufficientFundsException`, `CurrencyMismatchException` (Tasks 2–3).
- Produces: `Account` with `static Account open(String ownerName, Money initialBalance)`, `static Account reconstitute(AccountId id, String ownerName, Money balance)`, `void deposit(Money)`, `void withdraw(Money)`, `AccountId id()`, `String ownerName()`, `Money balance()`. Later tasks (infrastructure mapper, use cases, tests) rely on exactly these names.

- [ ] **Step 1: Write the failing test**

`AccountTest.java`:

```java
package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import com.testingai.banking.ledger.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void openingAnAccountRegistersAccountOpenedEvent() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(account.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(AccountOpened.class);
    }

    @Test
    void openingWithNegativeInitialBalanceIsRejected() {
        assertThatThrownBy(() -> Account.open("Ada Lovelace", Money.of(new BigDecimal("-1.00"), "USD")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void depositIncreasesBalanceAndRegistersMoneyDeposited() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        account.pullDomainEvents();

        account.deposit(Money.of(new BigDecimal("50.00"), "USD"));

        assertThat(account.balance().amount()).isEqualByComparingTo("150.00");
        assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(MoneyDeposited.class);
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        assertThatThrownBy(() -> account.deposit(Money.of(BigDecimal.ZERO, "USD")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void depositRejectsMismatchedCurrency() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        assertThatThrownBy(() -> account.deposit(Money.of(new BigDecimal("10.00"), "EUR")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void withdrawDecreasesBalanceAndRegistersMoneyWithdrawn() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        account.pullDomainEvents();

        account.withdraw(Money.of(new BigDecimal("40.00"), "USD"));

        assertThat(account.balance().amount()).isEqualByComparingTo("60.00");
        assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(MoneyWithdrawn.class);
    }

    @Test
    void withdrawBeyondBalanceIsRejected() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("10.00"), "USD"));

        assertThatThrownBy(() -> account.withdraw(Money.of(new BigDecimal("20.00"), "USD")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.balance().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void withdrawRejectsNonPositiveAmount() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        assertThatThrownBy(() -> account.withdraw(Money.of(BigDecimal.ZERO, "USD")))
                .isInstanceOf(InvalidAmountException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountTest`
Expected: FAIL to compile — `Account` doesn't exist.

- [ ] **Step 3: Write `Account`**

`ledger/domain/Account.java`:

```java
package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import com.testingai.banking.ledger.domain.exception.InvalidAmountException;
import java.time.Instant;

public final class Account extends AggregateRoot {

    private final AccountId id;
    private final String ownerName;
    private Money balance;

    private Account(AccountId id, String ownerName, Money balance) {
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
    }

    public static Account open(String ownerName, Money initialBalance) {
        if (initialBalance.isNegative()) {
            throw new InvalidAmountException("Initial balance must not be negative");
        }
        AccountId id = AccountId.newId();
        Account account = new Account(id, ownerName, initialBalance);
        account.registerEvent(new AccountOpened(id, ownerName, initialBalance, Instant.now()));
        return account;
    }

    public static Account reconstitute(AccountId id, String ownerName, Money balance) {
        return new Account(id, ownerName, balance);
    }

    public void deposit(Money amount) {
        requirePositive(amount);
        this.balance = this.balance.plus(amount);
        registerEvent(new MoneyDeposited(id, amount, Instant.now()));
    }

    public void withdraw(Money amount) {
        requirePositive(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(
                    "Account %s has insufficient funds for withdrawal of %s"
                            .formatted(id.value(), amount.amount()));
        }
        this.balance = this.balance.minus(amount);
        registerEvent(new MoneyWithdrawn(id, amount, Instant.now()));
    }

    private void requirePositive(Money amount) {
        if (amount.amount().signum() <= 0) {
            throw new InvalidAmountException("Amount must be positive: " + amount.amount());
        }
    }

    public AccountId id() {
        return id;
    }

    public String ownerName() {
        return ownerName;
    }

    public Money balance() {
        return balance;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/Account.java \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/AccountTest.java
git commit -m "feat(domain-driven-design): add Account aggregate with invariant enforcement"
```

---

### Task 5: `TransferService`, `AccountRepository` port, `LedgerConfig`

**Files:**
- Create: `.../ledger/domain/TransferService.java`
- Create: `.../ledger/domain/AccountRepository.java`
- Create: `.../ledger/LedgerConfig.java`
- Test: `.../ledger/domain/TransferServiceTest.java`

**Interfaces:**
- Consumes: `Account`, `Money` (Task 4).
- Produces: `TransferService.transfer(Account source, Account target, Money amount)` (withdraws from source, deposits to target — no return value, no transfer id); `AccountRepository` port with `Account save(Account)` / `Optional<Account> findById(AccountId)`; a `@Configuration` bean `TransferService transferService()` so use cases can be constructor-injected without putting `@Component` on a domain class.

- [ ] **Step 1: Write the failing test**

`TransferServiceTest.java`:

```java
package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private final TransferService transferService = new TransferService();

    @Test
    void transferWithdrawsFromSourceAndDepositsToTarget() {
        Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
        source.pullDomainEvents();
        target.pullDomainEvents();

        transferService.transfer(source, target, Money.of(new BigDecimal("30.00"), "USD"));

        assertThat(source.balance().amount()).isEqualByComparingTo("70.00");
        assertThat(target.balance().amount()).isEqualByComparingTo("40.00");
        assertThat(source.pullDomainEvents()).hasSize(1);
        assertThat(target.pullDomainEvents()).hasSize(1);
    }

    @Test
    void transferLeavesTargetUntouchedWhenSourceHasInsufficientFunds() {
        Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("5.00"), "USD"));
        Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
        source.pullDomainEvents();
        target.pullDomainEvents();

        assertThatThrownBy(
                        () -> transferService.transfer(source, target, Money.of(new BigDecimal("30.00"), "USD")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(target.balance().amount()).isEqualByComparingTo("10.00");
        assertThat(target.pullDomainEvents()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=TransferServiceTest`
Expected: FAIL to compile — `TransferService` doesn't exist.

- [ ] **Step 3: Write `TransferService`, `AccountRepository`, `LedgerConfig`**

`ledger/domain/TransferService.java`:

```java
package com.testingai.banking.ledger.domain;

public class TransferService {

    public void transfer(Account source, Account target, Money amount) {
        source.withdraw(amount);
        target.deposit(amount);
    }
}
```

`ledger/domain/AccountRepository.java`:

```java
package com.testingai.banking.ledger.domain;

import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId id);
}
```

`ledger/LedgerConfig.java`:

```java
package com.testingai.banking.ledger;

import com.testingai.banking.ledger.domain.TransferService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LedgerConfig {

    @Bean
    TransferService transferService() {
        return new TransferService();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=TransferServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/TransferService.java \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/domain/AccountRepository.java \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/LedgerConfig.java \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/domain/TransferServiceTest.java
git commit -m "feat(domain-driven-design): add TransferService domain service and AccountRepository port"
```

---

### Task 6: Ledger infrastructure (JPA adapter)

**Files:**
- Create: `.../ledger/infrastructure/AccountJpaEntity.java`
- Create: `.../ledger/infrastructure/AccountMapper.java`
- Create: `.../ledger/infrastructure/SpringDataAccountRepository.java`
- Create: `.../ledger/infrastructure/JpaAccountRepositoryAdapter.java`
- Test: `.../ledger/infrastructure/AccountPersistenceTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountId`, `Money`, `AccountRepository` (Tasks 3–5).
- Produces: `AccountMapper.toEntity(Account) -> AccountJpaEntity`, `AccountMapper.toDomain(AccountJpaEntity) -> Account`; `JpaAccountRepositoryAdapter implements AccountRepository`, registered as a `@Component` bean — this is what `LedgerConfig`'s consumers (the use cases in Task 7) get autowired as `AccountRepository`.

- [ ] **Step 1: Write the failing test**

`AccountPersistenceTest.java`:

```java
package com.testingai.banking.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AccountPersistenceTest {

    @Autowired
    private SpringDataAccountRepository springDataAccountRepository;

    @Test
    void savesAndReloadsAccountPreservingMoneyAndIdentity() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        springDataAccountRepository.save(AccountMapper.toEntity(account));
        AccountJpaEntity reloaded = springDataAccountRepository.findById(account.id().value()).orElseThrow();
        Account reconstituted = AccountMapper.toDomain(reloaded);

        assertThat(reconstituted.id()).isEqualTo(account.id());
        assertThat(reconstituted.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(reconstituted.balance().amount()).isEqualByComparingTo("100.00");
        assertThat(reconstituted.balance().currency().getCurrencyCode()).isEqualTo("USD");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountPersistenceTest`
Expected: FAIL to compile — `AccountJpaEntity`, `AccountMapper`, `SpringDataAccountRepository` don't exist.

- [ ] **Step 3: Write the JPA entity, mapper, and repositories**

`ledger/infrastructure/AccountJpaEntity.java`:

```java
package com.testingai.banking.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccountJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(nullable = false, length = 3)
    private String balanceCurrency;
}
```

`ledger/infrastructure/AccountMapper.java`:

```java
package com.testingai.banking.ledger.infrastructure;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.util.Currency;

public final class AccountMapper {

    private AccountMapper() {}

    public static AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
                account.id().value(),
                account.ownerName(),
                account.balance().amount(),
                account.balance().currency().getCurrencyCode());
    }

    public static Account toDomain(AccountJpaEntity entity) {
        return Account.reconstitute(
                new AccountId(entity.getId()),
                entity.getOwnerName(),
                new Money(entity.getBalanceAmount(), Currency.getInstance(entity.getBalanceCurrency())));
    }
}
```

`ledger/infrastructure/SpringDataAccountRepository.java`:

```java
package com.testingai.banking.ledger.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {}
```

`ledger/infrastructure/JpaAccountRepositoryAdapter.java`:

```java
package com.testingai.banking.ledger.infrastructure;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository springDataAccountRepository;

    public JpaAccountRepositoryAdapter(SpringDataAccountRepository springDataAccountRepository) {
        this.springDataAccountRepository = springDataAccountRepository;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity saved = springDataAccountRepository.save(AccountMapper.toEntity(account));
        return AccountMapper.toDomain(saved);
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return springDataAccountRepository.findById(id.value()).map(AccountMapper::toDomain);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountPersistenceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/infrastructure \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/infrastructure
git commit -m "feat(domain-driven-design): add JPA adapter for AccountRepository"
```

---

### Task 7: Ledger application use cases

**Files:**
- Create: `.../ledger/application/OpenAccountUseCase.java`
- Create: `.../ledger/application/DepositUseCase.java`
- Create: `.../ledger/application/WithdrawUseCase.java`
- Create: `.../ledger/application/TransferMoneyUseCase.java`
- Test: `.../ledger/application/InMemoryAccountRepository.java` (test fake, package-private)
- Test: `.../ledger/application/OpenAccountUseCaseTest.java`
- Test: `.../ledger/application/DepositUseCaseTest.java`
- Test: `.../ledger/application/WithdrawUseCaseTest.java`
- Test: `.../ledger/application/TransferMoneyUseCaseTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountId`, `Money`, `AccountRepository`, `TransferService` (Tasks 4–5).
- Produces: `OpenAccountUseCase.open(String, Money) -> Account`; `DepositUseCase.deposit(AccountId, Money) -> Account`; `WithdrawUseCase.withdraw(AccountId, Money) -> Account`; `TransferMoneyUseCase.transfer(AccountId from, AccountId to, Money) -> UUID` — these four exact method names/signatures are what the `web` controllers in Task 8 call.

- [ ] **Step 1: Write the test fake and the failing tests**

`InMemoryAccountRepository.java` (test-only fake, shared by all four use-case tests):

```java
package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryAccountRepository implements AccountRepository {

    private final Map<AccountId, Account> accounts = new HashMap<>();

    @Override
    public Account save(Account account) {
        accounts.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }
}
```

`OpenAccountUseCaseTest.java`:

```java
package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.AccountOpened;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OpenAccountUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
    private final OpenAccountUseCase useCase = new OpenAccountUseCase(repository, eventPublisher);

    @Test
    void opensAccountAndPublishesAccountOpenedEvent() {
        var account = useCase.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

        assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(repository.findById(account.id())).isPresent();
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(AccountOpened.class);
    }
}
```

`DepositUseCaseTest.java`:

```java
package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DepositUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
    private final DepositUseCase useCase = new DepositUseCase(repository, eventPublisher);

    @Test
    void depositsIntoExistingAccountAndPublishesEvent() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        account.pullDomainEvents();
        repository.save(account);

        Account updated = useCase.deposit(account.id(), Money.of(new BigDecimal("50.00"), "USD"));

        assertThat(updated.balance().amount()).isEqualByComparingTo("150.00");
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(MoneyDeposited.class);
    }
}
```

`WithdrawUseCaseTest.java`:

```java
package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class WithdrawUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
    private final WithdrawUseCase useCase = new WithdrawUseCase(repository, eventPublisher);

    @Test
    void withdrawsFromExistingAccountAndPublishesEvent() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        account.pullDomainEvents();
        repository.save(account);

        Account updated = useCase.withdraw(account.id(), Money.of(new BigDecimal("40.00"), "USD"));

        assertThat(updated.balance().amount()).isEqualByComparingTo("60.00");
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(MoneyWithdrawn.class);
    }

    @Test
    void rejectsWithdrawalBeyondBalance() {
        Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("10.00"), "USD"));
        account.pullDomainEvents();
        repository.save(account);

        assertThatThrownBy(() -> useCase.withdraw(account.id(), Money.of(new BigDecimal("20.00"), "USD")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(publishedEvents).isEmpty();
    }
}
```

`TransferMoneyUseCaseTest.java`:

```java
package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.TransferService;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TransferMoneyUseCaseTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
    private final TransferMoneyUseCase useCase =
            new TransferMoneyUseCase(repository, new TransferService(), eventPublisher);

    @Test
    void transfersBetweenTwoAccountsAndPublishesBothEvents() {
        Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
        source.pullDomainEvents();
        Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
        target.pullDomainEvents();
        repository.save(source);
        repository.save(target);

        useCase.transfer(source.id(), target.id(), Money.of(new BigDecimal("30.00"), "USD"));

        assertThat(repository.findById(source.id()).orElseThrow().balance().amount())
                .isEqualByComparingTo("70.00");
        assertThat(repository.findById(target.id()).orElseThrow().balance().amount())
                .isEqualByComparingTo("40.00");
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    void doesNotCreditTargetWhenSourceHasInsufficientFunds() {
        Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("5.00"), "USD"));
        source.pullDomainEvents();
        Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
        target.pullDomainEvents();
        repository.save(source);
        repository.save(target);

        assertThatThrownBy(
                        () -> useCase.transfer(source.id(), target.id(), Money.of(new BigDecimal("30.00"), "USD")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(repository.findById(target.id()).orElseThrow().balance().amount())
                .isEqualByComparingTo("10.00");
        assertThat(publishedEvents).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl banking/spring-demo test -Dtest=OpenAccountUseCaseTest,DepositUseCaseTest,WithdrawUseCaseTest,TransferMoneyUseCaseTest`
Expected: FAIL to compile — the four use case classes don't exist.

- [ ] **Step 3: Write the four use cases**

`ledger/application/OpenAccountUseCase.java`:

```java
package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAccountUseCase {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OpenAccountUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Account open(String ownerName, Money initialBalance) {
        Account account = Account.open(ownerName, initialBalance);
        Account saved = accountRepository.save(account);
        account.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }
}
```

`ledger/application/DepositUseCase.java`:

```java
package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositUseCase {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DepositUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Account deposit(AccountId accountId, Money amount) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId.value()));
        account.deposit(amount);
        Account saved = accountRepository.save(account);
        account.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }
}
```

`ledger/application/WithdrawUseCase.java`:

```java
package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawUseCase {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WithdrawUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Account withdraw(AccountId accountId, Money amount) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId.value()));
        account.withdraw(amount);
        Account saved = accountRepository.save(account);
        account.pullDomainEvents().forEach(eventPublisher::publishEvent);
        return saved;
    }
}
```

`ledger/application/TransferMoneyUseCase.java`:

```java
package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.TransferService;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferMoneyUseCase {

    private final AccountRepository accountRepository;
    private final TransferService transferService;
    private final ApplicationEventPublisher eventPublisher;

    public TransferMoneyUseCase(
            AccountRepository accountRepository,
            TransferService transferService,
            ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transferService = transferService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UUID transfer(AccountId fromAccountId, AccountId toAccountId, Money amount) {
        Account source = accountRepository
                .findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccountId.value()));
        Account target = accountRepository
                .findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccountId.value()));

        transferService.transfer(source, target, amount);

        accountRepository.save(source);
        accountRepository.save(target);

        source.pullDomainEvents().forEach(eventPublisher::publishEvent);
        target.pullDomainEvents().forEach(eventPublisher::publishEvent);

        return UUID.randomUUID();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl banking/spring-demo test -Dtest=OpenAccountUseCaseTest,DepositUseCaseTest,WithdrawUseCaseTest,TransferMoneyUseCaseTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/application \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/application
git commit -m "feat(domain-driven-design): add ledger application use cases"
```

---

### Task 8: Ledger web — `AccountController`, `TransferController`, DTOs

**Files:**
- Create: `.../ledger/web/dto/OpenAccountRequest.java`
- Create: `.../ledger/web/dto/OpenAccountResponse.java`
- Create: `.../ledger/web/dto/AmountRequest.java`
- Create: `.../ledger/web/dto/AccountResponse.java`
- Create: `.../ledger/web/dto/TransferRequest.java`
- Create: `.../ledger/web/dto/TransferResponse.java`
- Create: `.../ledger/web/AccountController.java`
- Create: `.../ledger/web/TransferController.java`
- Test: `.../ledger/web/AccountControllerTest.java`

**Interfaces:**
- Consumes: `OpenAccountUseCase`, `DepositUseCase`, `WithdrawUseCase`, `TransferMoneyUseCase`, `AccountRepository` (Tasks 6–7).
- Produces: `POST /accounts`, `GET /accounts/{id}`, `POST /accounts/{id}/deposits`, `POST /accounts/{id}/withdrawals`, `POST /transfers` — the endpoints Task 9–13's tests and the Gatling simulation (Task 14) call.

- [ ] **Step 1: Write the failing test**

`AccountControllerTest.java`:

```java
package com.testingai.banking.ledger.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensDepositsWithdrawsAndTransfersBetweenAccounts() throws Exception {
        String aliceId = openAccount("Alice", "200.00", "USD");
        String bobId = openAccount("Bob", "50.00", "USD");

        mockMvc.perform(post("/accounts/" + aliceId + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));

        mockMvc.perform(post("/accounts/" + aliceId + "/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"" + aliceId + "\",\"toAccountId\":\"" + bobId
                                + "\",\"amount\":75.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").exists());

        mockMvc.perform(get("/accounts/" + aliceId)).andExpect(jsonPath("$.balance").value(175.00));
        mockMvc.perform(get("/accounts/" + bobId)).andExpect(jsonPath("$.balance").value(125.00));
    }

    private String openAccount(String ownerName, String initialBalance, String currency) throws Exception {
        String response = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + ownerName + "\",\"initialBalance\":" + initialBalance
                                + ",\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.accountId");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountControllerTest`
Expected: FAIL — 404s, since no controller exists yet.

- [ ] **Step 3: Write the DTOs and controllers**

`ledger/web/dto/OpenAccountRequest.java`:

```java
package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record OpenAccountRequest(String ownerName, BigDecimal initialBalance, String currency) {}
```

`ledger/web/dto/OpenAccountResponse.java`:

```java
package com.testingai.banking.ledger.web.dto;

public record OpenAccountResponse(String accountId) {}
```

`ledger/web/dto/AmountRequest.java`:

```java
package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record AmountRequest(BigDecimal amount, String currency) {}
```

`ledger/web/dto/AccountResponse.java`:

```java
package com.testingai.banking.ledger.web.dto;

import com.testingai.banking.ledger.domain.Account;
import java.math.BigDecimal;

public record AccountResponse(String accountId, String ownerName, BigDecimal balance, String currency) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.id().value().toString(),
                account.ownerName(),
                account.balance().amount(),
                account.balance().currency().getCurrencyCode());
    }
}
```

`ledger/web/dto/TransferRequest.java`:

```java
package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount, String currency) {}
```

`ledger/web/dto/TransferResponse.java`:

```java
package com.testingai.banking.ledger.web.dto;

public record TransferResponse(String transferId) {}
```

`ledger/web/AccountController.java`:

```java
package com.testingai.banking.ledger.web;

import com.testingai.banking.ledger.application.DepositUseCase;
import com.testingai.banking.ledger.application.OpenAccountUseCase;
import com.testingai.banking.ledger.application.WithdrawUseCase;
import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import com.testingai.banking.ledger.web.dto.AccountResponse;
import com.testingai.banking.ledger.web.dto.AmountRequest;
import com.testingai.banking.ledger.web.dto.OpenAccountRequest;
import com.testingai.banking.ledger.web.dto.OpenAccountResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final OpenAccountUseCase openAccountUseCase;
    private final DepositUseCase depositUseCase;
    private final WithdrawUseCase withdrawUseCase;
    private final AccountRepository accountRepository;

    public AccountController(
            OpenAccountUseCase openAccountUseCase,
            DepositUseCase depositUseCase,
            WithdrawUseCase withdrawUseCase,
            AccountRepository accountRepository) {
        this.openAccountUseCase = openAccountUseCase;
        this.depositUseCase = depositUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.accountRepository = accountRepository;
    }

    @PostMapping
    public ResponseEntity<OpenAccountResponse> open(@RequestBody OpenAccountRequest request) {
        Account account =
                openAccountUseCase.open(request.ownerName(), Money.of(request.initialBalance(), request.currency()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OpenAccountResponse(account.id().value().toString()));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable String id) {
        AccountId accountId = new AccountId(UUID.fromString(id));
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
        return AccountResponse.from(account);
    }

    @PostMapping("/{id}/deposits")
    public AccountResponse deposit(@PathVariable String id, @RequestBody AmountRequest request) {
        Account account = depositUseCase.deposit(
                new AccountId(UUID.fromString(id)), Money.of(request.amount(), request.currency()));
        return AccountResponse.from(account);
    }

    @PostMapping("/{id}/withdrawals")
    public AccountResponse withdraw(@PathVariable String id, @RequestBody AmountRequest request) {
        Account account = withdrawUseCase.withdraw(
                new AccountId(UUID.fromString(id)), Money.of(request.amount(), request.currency()));
        return AccountResponse.from(account);
    }
}
```

`ledger/web/TransferController.java`:

```java
package com.testingai.banking.ledger.web;

import com.testingai.banking.ledger.application.TransferMoneyUseCase;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.web.dto.TransferRequest;
import com.testingai.banking.ledger.web.dto.TransferResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferMoneyUseCase transferMoneyUseCase;

    public TransferController(TransferMoneyUseCase transferMoneyUseCase) {
        this.transferMoneyUseCase = transferMoneyUseCase;
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@RequestBody TransferRequest request) {
        UUID transferId = transferMoneyUseCase.transfer(
                new AccountId(UUID.fromString(request.fromAccountId())),
                new AccountId(UUID.fromString(request.toAccountId())),
                Money.of(request.amount(), request.currency()));
        return new TransferResponse(transferId.toString());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=AccountControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/ledger/web \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/ledger/web
git commit -m "feat(domain-driven-design): add ledger REST API (accounts, deposits, withdrawals, transfers)"
```

---

### Task 9: Statements domain + infrastructure (JPA adapter)

**Files:**
- Create: `.../statements/domain/StatementLineType.java`
- Create: `.../statements/domain/StatementLine.java`
- Create: `.../statements/domain/StatementRepository.java`
- Create: `.../statements/infrastructure/StatementLineJpaEntity.java`
- Create: `.../statements/infrastructure/StatementLineMapper.java`
- Create: `.../statements/infrastructure/SpringDataStatementRepository.java`
- Create: `.../statements/infrastructure/JpaStatementRepositoryAdapter.java`
- Test: `.../statements/infrastructure/StatementPersistenceTest.java`

**Interfaces:**
- Produces: `StatementLine(UUID id, String accountId, StatementLineType type, BigDecimal amount, String currencyCode, String description, Instant occurredAt)`; `StatementRepository` port with `void save(StatementLine)` / `List<StatementLine> findByAccountId(String)` (returns lines ordered oldest-first); `JpaStatementRepositoryAdapter implements StatementRepository`, a `@Component` — this is what Task 10's ACL and Task 11's controller depend on.

- [ ] **Step 1: Write the failing test**

`StatementPersistenceTest.java`:

```java
package com.testingai.banking.statements.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class StatementPersistenceTest {

    @Autowired
    private SpringDataStatementRepository springDataStatementRepository;

    @Test
    void savesAndFindsStatementLinesByAccountId() {
        String accountId = UUID.randomUUID().toString();
        StatementLine line = new StatementLine(
                UUID.randomUUID(),
                accountId,
                StatementLineType.CREDIT,
                new BigDecimal("100.00"),
                "USD",
                "Account opened",
                Instant.now());

        springDataStatementRepository.save(StatementLineMapper.toEntity(line));

        var found = springDataStatementRepository.findByAccountIdOrderByOccurredAtAsc(accountId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDescription()).isEqualTo("Account opened");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=StatementPersistenceTest`
Expected: FAIL to compile — none of the statements classes exist yet.

- [ ] **Step 3: Write the statements domain and infrastructure**

`statements/domain/StatementLineType.java`:

```java
package com.testingai.banking.statements.domain;

public enum StatementLineType {
    DEBIT,
    CREDIT
}
```

`statements/domain/StatementLine.java`:

```java
package com.testingai.banking.statements.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementLine(
        UUID id,
        String accountId,
        StatementLineType type,
        BigDecimal amount,
        String currencyCode,
        String description,
        Instant occurredAt) {}
```

`statements/domain/StatementRepository.java`:

```java
package com.testingai.banking.statements.domain;

import java.util.List;

public interface StatementRepository {

    void save(StatementLine line);

    List<StatementLine> findByAccountId(String accountId);
}
```

`statements/infrastructure/StatementLineJpaEntity.java`:

```java
package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "statement_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StatementLineJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatementLineType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant occurredAt;
}
```

`statements/infrastructure/StatementLineMapper.java`:

```java
package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLine;

public final class StatementLineMapper {

    private StatementLineMapper() {}

    public static StatementLineJpaEntity toEntity(StatementLine line) {
        return new StatementLineJpaEntity(
                line.id(),
                line.accountId(),
                line.type(),
                line.amount(),
                line.currencyCode(),
                line.description(),
                line.occurredAt());
    }

    public static StatementLine toDomain(StatementLineJpaEntity entity) {
        return new StatementLine(
                entity.getId(),
                entity.getAccountId(),
                entity.getType(),
                entity.getAmount(),
                entity.getCurrencyCode(),
                entity.getDescription(),
                entity.getOccurredAt());
    }
}
```

`statements/infrastructure/SpringDataStatementRepository.java`:

```java
package com.testingai.banking.statements.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataStatementRepository extends JpaRepository<StatementLineJpaEntity, UUID> {

    List<StatementLineJpaEntity> findByAccountIdOrderByOccurredAtAsc(String accountId);
}
```

`statements/infrastructure/JpaStatementRepositoryAdapter.java`:

```java
package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaStatementRepositoryAdapter implements StatementRepository {

    private final SpringDataStatementRepository springDataStatementRepository;

    public JpaStatementRepositoryAdapter(SpringDataStatementRepository springDataStatementRepository) {
        this.springDataStatementRepository = springDataStatementRepository;
    }

    @Override
    public void save(StatementLine line) {
        springDataStatementRepository.save(StatementLineMapper.toEntity(line));
    }

    @Override
    public List<StatementLine> findByAccountId(String accountId) {
        return springDataStatementRepository.findByAccountIdOrderByOccurredAtAsc(accountId).stream()
                .map(StatementLineMapper::toDomain)
                .toList();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=StatementPersistenceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/statements/domain \
  domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/statements/infrastructure \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/statements/infrastructure
git commit -m "feat(domain-driven-design): add statements context domain model and JPA adapter"
```

---

### Task 10: Anti-corruption layer — `LedgerEventTranslator`

**Files:**
- Create: `.../statements/infrastructure/acl/LedgerEventTranslator.java`
- Test: `.../statements/infrastructure/acl/LedgerEventTranslatorTest.java`

**Interfaces:**
- Consumes: `LedgerEvent`, `AccountOpened`, `MoneyDeposited`, `MoneyWithdrawn` (Task 3); `StatementLine`, `StatementLineType`, `StatementRepository` (Task 9).
- Produces: `LedgerEventTranslator`, a `@Component` with `@EventListener public void onLedgerEvent(LedgerEvent)` and a package-visible `StatementLine translate(LedgerEvent)` used directly by its own unit test. This is the **only** class in the codebase importing both `ledger.domain.event.*` and `statements.domain.*`.

- [ ] **Step 1: Write the failing test**

`LedgerEventTranslatorTest.java`:

```java
package com.testingai.banking.statements.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import com.testingai.banking.statements.domain.StatementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerEventTranslatorTest {

    private final List<StatementLine> savedLines = new ArrayList<>();
    private final StatementRepository fakeRepository = new StatementRepository() {
        @Override
        public void save(StatementLine line) {
            savedLines.add(line);
        }

        @Override
        public List<StatementLine> findByAccountId(String accountId) {
            return savedLines;
        }
    };
    private final LedgerEventTranslator translator = new LedgerEventTranslator(fakeRepository);

    @Test
    void translatesAccountOpenedToCreditLine() {
        AccountId accountId = AccountId.newId();
        var event = new AccountOpened(
                accountId, "Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"), Instant.now());

        StatementLine line = translator.translate(event);

        assertThat(line.accountId()).isEqualTo(accountId.value().toString());
        assertThat(line.type()).isEqualTo(StatementLineType.CREDIT);
        assertThat(line.amount()).isEqualByComparingTo("100.00");
        assertThat(line.description()).isEqualTo("Account opened");
    }

    @Test
    void translatesMoneyDepositedToCreditLine() {
        AccountId accountId = AccountId.newId();
        var event = new MoneyDeposited(accountId, Money.of(new BigDecimal("25.00"), "USD"), Instant.now());

        StatementLine line = translator.translate(event);

        assertThat(line.type()).isEqualTo(StatementLineType.CREDIT);
        assertThat(line.description()).isEqualTo("Deposit");
    }

    @Test
    void translatesMoneyWithdrawnToDebitLine() {
        AccountId accountId = AccountId.newId();
        var event = new MoneyWithdrawn(accountId, Money.of(new BigDecimal("15.00"), "USD"), Instant.now());

        StatementLine line = translator.translate(event);

        assertThat(line.type()).isEqualTo(StatementLineType.DEBIT);
        assertThat(line.description()).isEqualTo("Withdrawal");
    }

    @Test
    void onLedgerEventSavesTranslatedLineToRepository() {
        AccountId accountId = AccountId.newId();
        var event = new MoneyDeposited(accountId, Money.of(new BigDecimal("25.00"), "USD"), Instant.now());

        translator.onLedgerEvent(event);

        assertThat(savedLines).hasSize(1);
        assertThat(savedLines.get(0).description()).isEqualTo("Deposit");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=LedgerEventTranslatorTest`
Expected: FAIL to compile — `LedgerEventTranslator` doesn't exist.

- [ ] **Step 3: Write `LedgerEventTranslator`**

`statements/infrastructure/acl/LedgerEventTranslator.java`:

```java
package com.testingai.banking.statements.infrastructure.acl;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.LedgerEvent;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LedgerEventTranslator {

    private final StatementRepository statementRepository;

    public LedgerEventTranslator(StatementRepository statementRepository) {
        this.statementRepository = statementRepository;
    }

    @EventListener
    public void onLedgerEvent(LedgerEvent event) {
        statementRepository.save(translate(event));
    }

    StatementLine translate(LedgerEvent event) {
        return switch (event) {
            case AccountOpened(var accountId, var ownerName, var openingBalance, var occurredAt) ->
                new StatementLine(
                        UUID.randomUUID(),
                        accountId.value().toString(),
                        StatementLineType.CREDIT,
                        openingBalance.amount(),
                        openingBalance.currency().getCurrencyCode(),
                        "Account opened",
                        occurredAt);
            case MoneyDeposited(var accountId, var amount, var occurredAt) ->
                new StatementLine(
                        UUID.randomUUID(),
                        accountId.value().toString(),
                        StatementLineType.CREDIT,
                        amount.amount(),
                        amount.currency().getCurrencyCode(),
                        "Deposit",
                        occurredAt);
            case MoneyWithdrawn(var accountId, var amount, var occurredAt) ->
                new StatementLine(
                        UUID.randomUUID(),
                        accountId.value().toString(),
                        StatementLineType.DEBIT,
                        amount.amount(),
                        amount.currency().getCurrencyCode(),
                        "Withdrawal",
                        occurredAt);
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=LedgerEventTranslatorTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/statements/infrastructure/acl \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/statements/infrastructure/acl
git commit -m "feat(domain-driven-design): add LedgerEventTranslator anti-corruption layer"
```

---

### Task 11: Statements web — `StatementController`

**Files:**
- Create: `.../statements/web/StatementController.java`
- Test: `.../statements/web/StatementControllerTest.java`

**Interfaces:**
- Consumes: `StatementRepository` (Task 9), the running `LedgerEventTranslator` (Task 10) which populates it, and `AccountController`'s `/accounts` / `/accounts/{id}/deposits` endpoints (Task 8) to set up test fixtures.
- Produces: `GET /accounts/{accountId}/statement -> List<StatementLine>`.

- [ ] **Step 1: Write the failing test**

`StatementControllerTest.java`:

```java
package com.testingai.banking.statements.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StatementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openingAndDepositingProducesTwoCreditStatementLinesInOrder() throws Exception {
        String openResponse = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada Lovelace\",\"initialBalance\":100.00,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accountId = com.jayway.jsonpath.JsonPath.read(openResponse, "$.accountId");

        mockMvc.perform(post("/accounts/" + accountId + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/accounts/" + accountId + "/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Account opened"))
                .andExpect(jsonPath("$[0].type").value("CREDIT"))
                .andExpect(jsonPath("$[1].description").value("Deposit"))
                .andExpect(jsonPath("$[1].type").value("CREDIT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=StatementControllerTest`
Expected: FAIL — `GET /accounts/{id}/statement` 404s, no controller exists.

- [ ] **Step 3: Write `StatementController`**

`statements/web/StatementController.java`:

```java
package com.testingai.banking.statements.web;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/{accountId}/statement")
public class StatementController {

    private final StatementRepository statementRepository;

    public StatementController(StatementRepository statementRepository) {
        this.statementRepository = statementRepository;
    }

    @GetMapping
    public List<StatementLine> getStatement(@PathVariable String accountId) {
        return statementRepository.findByAccountId(accountId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=StatementControllerTest`
Expected: PASS. This is the first test that proves the ledger→ACL→statements wiring works end to end.

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/statements/web \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/statements/web
git commit -m "feat(domain-driven-design): add statements REST API and prove the ACL wiring end to end"
```

---

### Task 12: `DomainExceptionHandler` and error-path tests

**Files:**
- Create: `.../web/DomainExceptionHandler.java`
- Test: `.../web/BankingErrorPathTest.java`

**Interfaces:**
- Consumes: `DomainException`, `AccountNotFoundException` (Task 2).
- Produces: `DomainExceptionHandler`, a `@RestControllerAdvice` mapping `AccountNotFoundException` → 404 and any other `DomainException` → 400, both with a `{error, message}` JSON body.

- [ ] **Step 1: Write the failing test**

`BankingErrorPathTest.java`:

```java
package com.testingai.banking.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BankingErrorPathTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withdrawingBeyondBalanceReturns400() throws Exception {
        String accountId = openAccount("10.00");

        mockMvc.perform(post("/accounts/" + accountId + "/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InsufficientFundsException"));
    }

    @Test
    void gettingUnknownAccountReturns404() throws Exception {
        mockMvc.perform(get("/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void depositingMismatchedCurrencyReturns400() throws Exception {
        String accountId = openAccount("10.00");

        mockMvc.perform(post("/accounts/" + accountId + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CurrencyMismatchException"));
    }

    private String openAccount(String initialBalance) throws Exception {
        String response = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada Lovelace\",\"initialBalance\":" + initialBalance
                                + ",\"currency\":\"USD\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.accountId");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl banking/spring-demo test -Dtest=BankingErrorPathTest`
Expected: FAIL — no exception mapping exists yet, so these all currently return `500`.

- [ ] **Step 3: Write `DomainExceptionHandler`**

`web/DomainExceptionHandler.java`:

```java
package com.testingai.banking.web;

import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import com.testingai.banking.ledger.domain.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AccountNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "ACCOUNT_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(DomainException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", exception.getClass().getSimpleName(), "message", exception.getMessage()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=BankingErrorPathTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/main/java/com/testingai/banking/web \
  domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/web/BankingErrorPathTest.java
git commit -m "feat(domain-driven-design): map domain exceptions to 400/404 HTTP responses"
```

---

### Task 13: Full golden-path integration test

**Files:**
- Test: `.../web/BankingIntegrationTest.java`

**Interfaces:**
- Consumes: every endpoint from Tasks 8, 11, 12. No new production code — this task only adds the test that proves the whole system (ledger use cases + persistence + ACL + statements) works together.

- [ ] **Step 1: Write the test**

`BankingIntegrationTest.java`:

```java
package com.testingai.banking.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BankingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openDepositWithdrawTransferAndStatementsReflectEverything() throws Exception {
        String aliceId = openAccount("Alice", "200.00", "USD");
        String bobId = openAccount("Bob", "50.00", "USD");

        mockMvc.perform(post("/accounts/" + aliceId + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));

        mockMvc.perform(post("/accounts/" + aliceId + "/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"" + aliceId + "\",\"toAccountId\":\"" + bobId
                                + "\",\"amount\":75.00,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").exists());

        mockMvc.perform(get("/accounts/" + aliceId)).andExpect(jsonPath("$.balance").value(175.00));
        mockMvc.perform(get("/accounts/" + bobId)).andExpect(jsonPath("$.balance").value(125.00));

        mockMvc.perform(get("/accounts/" + aliceId + "/statement"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].description").value("Account opened"))
                .andExpect(jsonPath("$[1].description").value("Deposit"))
                .andExpect(jsonPath("$[2].description").value("Withdrawal"))
                .andExpect(jsonPath("$[3].description").value("Withdrawal"));

        mockMvc.perform(get("/accounts/" + bobId + "/statement"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Account opened"))
                .andExpect(jsonPath("$[1].description").value("Deposit"));
    }

    private String openAccount(String ownerName, String initialBalance, String currency) throws Exception {
        String response = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + ownerName + "\",\"initialBalance\":" + initialBalance
                                + ",\"currency\":\"" + currency + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.accountId");
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn -pl banking/spring-demo test -Dtest=BankingIntegrationTest`
Expected: PASS. Alice's statement has 4 lines because her withdrawal and the transfer's debit are both plain `MoneyWithdrawn` events (per the Global Constraints note on `transferId`); Bob's statement has 2 because the transfer's credit is a plain `MoneyDeposited`.

- [ ] **Step 3: Run the full test suite**

Run: `mvn -pl banking/spring-demo test` (from `domain-driven-design/`)
Expected: `BUILD SUCCESS`, all tests across every earlier task still pass.

- [ ] **Step 4: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/web/BankingIntegrationTest.java
git commit -m "test(domain-driven-design): add end-to-end golden-path test across ledger, ACL, and statements"
```

---

### Task 14: Gatling load test

**Files:**
- Create: `.../src/test/java/com/testingai/banking/performance/BankingSimulation.java`

**Interfaces:**
- Consumes: `POST /accounts`, `POST /accounts/{id}/deposits`, `POST /accounts/{id}/withdrawals`, `GET /accounts/{id}`, `GET /accounts/{id}/statement`, `POST /transfers` (Tasks 8, 11) against a **running** app on `8099`.

- [ ] **Step 1: Write `BankingSimulation`**

`performance/BankingSimulation.java`:

```java
package com.testingai.banking.performance;

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

public class BankingSimulation extends Simulation {

	private static final String OPEN_ACCOUNT_BODY = """
			{"ownerName":"Load Test User","initialBalance":1000.00,"currency":"USD"}""";

	private static final String DEPOSIT_BODY = """
			{"amount":50.00,"currency":"USD"}""";

	private static final String WITHDRAW_BODY = """
			{"amount":20.00,"currency":"USD"}""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8099")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder ledgerScenario = scenario("Ledger Operations")
			.exec(http("Open Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY))
					.check(status().is(201)).check(jsonPath("$.accountId").saveAs("accountId")))
			.exec(http("Deposit").post("/accounts/#{accountId}/deposits").body(StringBody(DEPOSIT_BODY))
					.check(status().is(200)))
			.exec(http("Withdraw").post("/accounts/#{accountId}/withdrawals").body(StringBody(WITHDRAW_BODY))
					.check(status().is(200)))
			.exec(http("Get Account").get("/accounts/#{accountId}").check(status().is(200)))
			.exec(http("Get Statement").get("/accounts/#{accountId}/statement").check(status().is(200)));

	private final ScenarioBuilder transferScenario = scenario("Transfers")
			.exec(http("Open Source Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY))
					.check(status().is(201)).check(jsonPath("$.accountId").saveAs("fromAccountId")))
			.exec(http("Open Target Account").post("/accounts").body(StringBody(OPEN_ACCOUNT_BODY))
					.check(status().is(201)).check(jsonPath("$.accountId").saveAs("toAccountId")))
			.exec(http("Transfer").post("/transfers")
					.body(StringBody(
							"{\"fromAccountId\":\"#{fromAccountId}\",\"toAccountId\":\"#{toAccountId}\",\"amount\":10.00,\"currency\":\"USD\"}"))
					.check(status().is(200)));

	{
		setUp(ledgerScenario.injectOpen(atOnceUsers(10)), transferScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it runs against a live app**

Run: `mvn -pl banking/spring-demo spring-boot:run` (from `domain-driven-design/`, background/new terminal)
Run: `mvn -pl banking/spring-demo gatling:test` (from `domain-driven-design/`, once the app is up)
Expected: Gatling report generated with 0 failed requests. Stop the app afterward.

- [ ] **Step 3: Commit**

```bash
git add domain-driven-design/banking/spring-demo/src/test/java/com/testingai/banking/performance
git commit -m "test(domain-driven-design): add Gatling load test for the banking ledger demo"
```

---

### Task 15: Documentation

**Files:**
- Create: `domain-driven-design/README.md`
- Create: `domain-driven-design/banking/spring-demo/README.md`
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- None — pure documentation, no code dependencies. This task can be verified only by re-running the commands it documents.

- [ ] **Step 1: Write the category README**

`domain-driven-design/README.md`:

```markdown
# Domain-Driven Design

Demo modules for tactical Domain-Driven Design patterns.

## banking/spring-demo

A banking ledger demonstrating:

- **Entities & aggregates** — `Account` enforces its own invariants (no negative balances, no currency mixing) rather than leaving validation to a service layer.
- **Value objects** — `AccountId`, `Money`.
- **Domain events** — `AccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, a sealed hierarchy.
- **Repository as a port** — `AccountRepository` is a domain interface; `JpaAccountRepositoryAdapter` is the only class that knows about JPA.
- **Domain service** — `TransferService` coordinates two aggregates for a transfer, which no single `Account` can do on its own.
- **Bounded contexts + anti-corruption layer** — a second context, `statements`, maintains its own read model (`StatementLine`) built exclusively by translating `ledger`'s domain events in `LedgerEventTranslator`, without ever depending on `ledger`'s domain types anywhere else.

```
ledger (domain core) --publishes--> LedgerEvent --consumed by--> statements.acl.LedgerEventTranslator --writes--> statements (its own read model)
```

No Docker required — the app runs entirely on an in-memory H2 database. See `banking/spring-demo/README.md` for how to run it.
```

- [ ] **Step 2: Write the module README**

`domain-driven-design/banking/spring-demo/README.md`:

```markdown
# DDD Banking Ledger Demo

Demonstrates tactical Domain-Driven Design patterns via a banking ledger: open an account, deposit, withdraw, transfer — with a second bounded context (`statements`) that maintains its own account-statement read model, fed only through an anti-corruption layer.

## Prerequisites

- Java 21
- Maven

No Docker needed — the app runs on an in-memory H2 database.

## Running

```bash
cd domain-driven-design
mvn -pl banking/spring-demo spring-boot:run
```

The app starts on `8099`. Swagger UI: http://localhost:8099/swagger-ui/index.html

## Patterns demonstrated

| Pattern | Class |
|---|---|
| Aggregate root with invariants | `ledger.domain.Account` |
| Value object | `ledger.domain.Money`, `ledger.domain.AccountId` |
| Domain event | `ledger.domain.event.LedgerEvent` (sealed) and its variants |
| Repository as a port | `ledger.domain.AccountRepository` (interface) / `ledger.infrastructure.JpaAccountRepositoryAdapter` (adapter) |
| Domain service across aggregates | `ledger.domain.TransferService` |
| Bounded context | `statements` — its own `StatementLine` model, never importing `ledger`'s types |
| Anti-corruption layer | `statements.infrastructure.acl.LedgerEventTranslator` |

## Walkthrough

Open two accounts:

```bash
curl -s -X POST localhost:8099/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","initialBalance":200.00,"currency":"USD"}'
# => {"accountId":"<alice-id>"}

curl -s -X POST localhost:8099/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","initialBalance":50.00,"currency":"USD"}'
# => {"accountId":"<bob-id>"}
```

Deposit into Alice's account:

```bash
curl -s -X POST localhost:8099/accounts/<alice-id>/deposits -H 'Content-Type: application/json' \
  -d '{"amount":100.00,"currency":"USD"}'
# => {"accountId":"<alice-id>","ownerName":"Alice","balance":300.00,"currency":"USD"}
```

Try to withdraw more than the balance — rejected by the aggregate's own invariant, not a service-layer check:

```bash
curl -s -i -X POST localhost:8099/accounts/<alice-id>/withdrawals -H 'Content-Type: application/json' \
  -d '{"amount":10000.00,"currency":"USD"}'
# => HTTP/1.1 400
# => {"error":"InsufficientFundsException","message":"Account <alice-id> has insufficient funds for withdrawal of 10000.00"}
```

Transfer between the two accounts:

```bash
curl -s -X POST localhost:8099/transfers -H 'Content-Type: application/json' \
  -d '{"fromAccountId":"<alice-id>","toAccountId":"<bob-id>","amount":75.00,"currency":"USD"}'
# => {"transferId":"..."}
```

Check each account's statement — built entirely by the anti-corruption layer from `ledger`'s domain events, never read directly from `ledger`'s tables:

```bash
curl -s localhost:8099/accounts/<alice-id>/statement
curl -s localhost:8099/accounts/<bob-id>/statement
```

## Testing

```bash
mvn -pl banking/spring-demo test              # unit + integration tests (H2, Gatling excluded)
mvn -pl banking/spring-demo test -Dtest=AccountTest   # single test class
mvn -pl banking/spring-demo gatling:test      # load test — requires the app running first (see above)
```
```

- [ ] **Step 3: Update CLAUDE.md**

In `CLAUDE.md`, add a new command section right after "### Saga pattern demo ... (run from the module root, no docker infrastructure required)" block:

```markdown
### DDD banking ledger demo (run from the reactor root, no docker infrastructure required)

```bash
cd domain-driven-design

mvn clean package                                            # build (reactor build)
mvn test -pl banking/spring-demo                              # unit tests (Gatling excluded automatically)
mvn test -pl banking/spring-demo -Dtest=ClassName              # single test class
mvn -pl banking/spring-demo spring-boot:run                    # run the app (:8099)
mvn gatling:test -pl banking/spring-demo                       # Gatling load test — requires the app running first
```
```

And add a row to the repository layout table (after the `distributed-transactions/<pattern>/spring-demo/` row):

```markdown
| `domain-driven-design/banking/spring-demo/` | Tactical DDD demo — banking ledger with aggregate invariants, value objects, domain events, a repository port, a cross-aggregate domain service, and a second bounded context (`statements`) wired to the first only through an anti-corruption layer; no external infrastructure required (H2 only) |
```

- [ ] **Step 4: Update the root README**

In `README.md`, add a row to the repository layout table (after the `distributed-transactions/` row):

```markdown
| `domain-driven-design/` | Tactical DDD patterns — banking ledger with aggregates, value objects, domain events, bounded contexts, and an anti-corruption layer |
```

- [ ] **Step 5: Verify the documented commands actually work**

Run each command block from the new module README verbatim (build, run, the four `curl` steps, test, gatling:test) and confirm the output matches what's documented. Fix the docs if anything drifted.

- [ ] **Step 6: Commit**

```bash
git add domain-driven-design/README.md domain-driven-design/banking/spring-demo/README.md CLAUDE.md README.md
git commit -m "docs(domain-driven-design): document the banking ledger demo"
```
