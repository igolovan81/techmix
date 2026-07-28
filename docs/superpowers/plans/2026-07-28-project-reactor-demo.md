# Project Reactor Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `reactive-programming/project-reactor/` module with two Spring Boot WebFlux apps (`spring-demo`, `upstream-demo`) that demonstrate core Project Reactor concepts — Mono/Flux basics, backpressure/error handling, schedulers/concurrency, and SSE + WebClient streaming — behind a REST `DemoController`, matching this repo's existing demo-module conventions.

**Architecture:** `reactive-programming/pom.xml` is the Maven reactor root (packaging `pom`), mirroring `template-engines/pom.xml` and `communication-protocols/pom.xml` — its `<modules>` point directly into `project-reactor/spring-demo` and `project-reactor/upstream-demo`, with no intermediate `project-reactor/pom.xml` (matching how `communication-protocols/pom.xml` references `grpc/server-demo` without a `grpc/pom.xml`). `spring-demo` is the primary demo app (`DemoController`, package-per-pattern: `basics/`, `resilience/`, `concurrency/`, `streaming/`). `upstream-demo` is a small standalone WebFlux service that `spring-demo`'s `WebClient` calls for the streaming pattern.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring WebFlux, Project Reactor, Lombok, Spock/Groovy (`spock-core` 2.3-groovy-4.0, `groovy` 4.0.21, `gmavenplus-plugin`) for unit tests, Gatling for load testing, OkHttp `mockwebserver` for `WebClient` test stubbing.

## Global Constraints

- Java 21 — `maven.compiler.release` is `21` in every POM; the ambient `JAVA_HOME` for running `mvn` commands in this plan **must** point at a JDK 21 install (default JDK 25 breaks `gmavenplus`/Groovy compilation).
- No Docker / external infrastructure for this module.
- `FailureSimulator` must be a static utility class — `FAILURE_RATE = 0.05` (`private static final double`), `public static void maybeThrow(String context)` throwing `RuntimeException`, **no** `shouldFail()` method — exact shape of `message-brokers/kafka/spring-demo/src/main/java/com/testingai/kafka/util/FailureSimulator.java`.
- All instance fields assigned once (declaration or constructor) and never reassigned must be `private final` (per `.claude/rules/code-review.md`).
- Prefer records, pattern matching, text blocks, `SequencedCollection`, virtual threads over pre-modern Java idioms, per `.claude/rules/code-review.md` — applies to all new code in this plan.
- Controller-level Spock specs must use standalone `WebTestClient.bindToController(...)`, **not** `@WebFluxTest` — `spock-spring:2.3-groovy-4.0` cannot detect Spring Boot 3.x test-slice annotations under Spring Framework 6 (confirmed while converting `message-brokers/kafka`'s `@WebMvcTest` test; `@Autowired`/`@MockitoBean` fields silently stay `null`, no Spring startup log lines). Only `spock-core` is needed, not `spock-spring`.
- Ports: `spring-demo` → `8094`, `upstream-demo` → `8095`.

---

## Task 1: Scaffold the `reactive-programming` Maven reactor and both app skeletons

**Files:**
- Create: `reactive-programming/eclipse-formatter.xml` (copy of `template-engines/eclipse-formatter.xml`)
- Create: `reactive-programming/pom.xml`
- Create: `reactive-programming/README.md`
- Create: `reactive-programming/project-reactor/README.md`
- Create: `reactive-programming/project-reactor/upstream-demo/pom.xml`
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/resources/application.yml`
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/ReactorUpstreamDemoApplication.java`
- Test: `reactive-programming/project-reactor/upstream-demo/src/test/groovy/com/testingai/reactor/upstream/ReactorUpstreamDemoApplicationTest.groovy`
- Create: `reactive-programming/project-reactor/spring-demo/pom.xml`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/resources/application.yml`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/ReactorDemoApplication.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/ReactorDemoApplicationTest.groovy`
- Modify: `.githooks/pre-commit`
- Modify: `CLAUDE.md`

**Interfaces:**
- Produces: `com.testingai.reactor.upstream.ReactorUpstreamDemoApplication` (main class, `upstream-demo`), `com.testingai.reactor.ReactorDemoApplication` (main class, `spring-demo`). Both are plain `@SpringBootApplication` classes with no constructor arguments — later tasks add packages under these same base packages.
- Consumes: nothing (first task).

- [ ] **Step 1: Copy the shared Eclipse formatter config**

```bash
cp /Users/admin/IdeaProjects/private/techmix-copy/template-engines/eclipse-formatter.xml \
   /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming/eclipse-formatter.xml
```

- [ ] **Step 2: Create the reactor-root parent POM**

`reactive-programming/pom.xml`:

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
    <artifactId>reactive-programming</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Reactive Programming</name>
    <description>Parent POM for all reactive-programming demo modules</description>

    <modules>
        <module>project-reactor/spring-demo</module>
        <module>project-reactor/upstream-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <groovy.version>4.0.21</groovy.version>
        <spock.version>2.3-groovy-4.0</spock.version>
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

- [ ] **Step 3: Create the category README**

`reactive-programming/README.md`:

```markdown
# Reactive Programming — Demos

This directory contains runnable demos for reactive-programming libraries on the JVM, structured the same way as `../template-engines/`: one or more Spring Boot demo apps per library, no external infrastructure required.

| Library | Demo | Best fit |
|---|---|---|
| [Project Reactor](project-reactor/) | `spring-boot-starter-webflux` | Mono/Flux fundamentals, backpressure, schedulers, and reactive HTTP streaming (SSE, WebClient) |

More reactive libraries may be added here over time (e.g. RxJava), at which point this README will grow into a comparison guide like `../message-brokers/README.md`.
```

- [ ] **Step 4: Create the module README**

`reactive-programming/project-reactor/README.md`:

```markdown
# Project Reactor Demo

Two independent Spring Boot WebFlux apps demonstrating core [Project Reactor](https://projectreactor.io/) concepts against a small product-catalog domain:

- **[`spring-demo`](spring-demo/)** — the primary demo app. Exposes every pattern below behind `DemoController`.
- **[`upstream-demo`](upstream-demo/)** — a small standalone service (`spring-demo`'s `WebClient` calls it) providing a product feed and a live SSE price-tick stream, so the "reactive streaming to another service" pattern has a real network hop to demonstrate.

## Concepts covered

| Package | Concepts |
|---|---|
| `basics/` | `Mono`/`Flux` creation (`just`, `fromIterable`, `generate`), composition (`map`/`filter`/`flatMap`, `zip`/`merge`/`concat`) |
| `resilience/` | Backpressure (`onBackpressureBuffer`/`onBackpressureDrop`), retry (`retryWhen`), fallback (`onErrorResume`), `timeout` |
| `concurrency/` | `subscribeOn` vs `publishOn`, `ParallelFlux`, offloading blocking calls to `Schedulers.boundedElastic()` |
| `streaming/` | SSE producer (own feed) and SSE/`WebClient` consumer (relaying `upstream-demo`'s feed) |

## Endpoints

| Endpoint | Pattern group |
|---|---|
| `GET /demo/basics/products` | Basics |
| `GET /demo/basics/products/{id}` | Basics |
| `GET /demo/basics/generated?count=N` | Basics |
| `GET /demo/basics/discounted` | Basics |
| `GET /demo/resilience/backpressure?strategy=buffer\|drop` | Resilience |
| `GET /demo/resilience/retry` | Resilience |
| `GET /demo/resilience/timeout` | Resilience |
| `GET /demo/concurrency/subscribe-vs-publish-on` | Concurrency |
| `GET /demo/concurrency/parallel` | Concurrency |
| `GET /demo/concurrency/blocking-offload` | Concurrency |
| `GET /demo/streaming/ticks` (SSE) | Streaming |
| `GET /demo/streaming/upstream/products` | Streaming |
| `GET /demo/streaming/upstream/ticks` (SSE) | Streaming |

## Running

No Docker required.

```bash
cd reactive-programming

# terminal 1 — upstream-demo must be running first for the streaming/upstream/* endpoints
mvn -pl project-reactor/upstream-demo spring-boot:run

# terminal 2
mvn -pl project-reactor/spring-demo spring-boot:run
```

`spring-demo` listens on `:8094`, `upstream-demo` on `:8095`. Swagger UI for `spring-demo` is at `http://localhost:8094/swagger-ui/index.html`.
```

- [ ] **Step 5: Create the `upstream-demo` POM**

`reactive-programming/project-reactor/upstream-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>reactive-programming</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>reactor-upstream-demo</artifactId>
    <name>Project Reactor Upstream Service</name>
    <description>Small reactive upstream service (products + live price ticks) consumed by reactor-demo's WebClient</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.groovy</groupId>
            <artifactId>groovy</artifactId>
            <version>${groovy.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.spockframework</groupId>
            <artifactId>spock-core</artifactId>
            <version>${spock.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.reactor.upstream.ReactorUpstreamDemoApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.gmavenplus</groupId>
                <artifactId>gmavenplus-plugin</artifactId>
                <version>3.0.2</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>addTestSources</goal>
                            <goal>compileTests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 6: Create the `upstream-demo` application config**

`reactive-programming/project-reactor/upstream-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8095
```

- [ ] **Step 7: Write the failing smoke test for `upstream-demo`**

`reactive-programming/project-reactor/upstream-demo/src/test/groovy/com/testingai/reactor/upstream/ReactorUpstreamDemoApplicationTest.groovy`:

```groovy
package com.testingai.reactor.upstream

import spock.lang.Specification

class ReactorUpstreamDemoApplicationTest extends Specification {

    def "main class exists"() {
        expect:
        new ReactorUpstreamDemoApplication()
    }
}
```

- [ ] **Step 8: Verify the test fails to compile**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/upstream-demo test`
Expected: FAIL — `ReactorUpstreamDemoApplication` does not exist (compilation error)

- [ ] **Step 9: Create the `upstream-demo` application class**

`reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/ReactorUpstreamDemoApplication.java`:

```java
package com.testingai.reactor.upstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactorUpstreamDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorUpstreamDemoApplication.class, args);
    }
}
```

- [ ] **Step 10: Verify the smoke test passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/upstream-demo test`
Expected: PASS

- [ ] **Step 11: Create the `spring-demo` POM**

`reactive-programming/project-reactor/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>reactive-programming</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>reactor-demo</artifactId>
    <name>Project Reactor Demo</name>
    <description>Learning and demonstration project for core Project Reactor concepts</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
            <version>2.8.6</version>
        </dependency>
        <dependency>
            <groupId>org.apache.groovy</groupId>
            <artifactId>groovy</artifactId>
            <version>${groovy.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.spockframework</groupId>
            <artifactId>spock-core</artifactId>
            <version>${spock.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
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
                    <mainClass>com.testingai.reactor.ReactorDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.reactor.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.gmavenplus</groupId>
                <artifactId>gmavenplus-plugin</artifactId>
                <version>3.0.2</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>addTestSources</goal>
                            <goal>compileTests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 12: Create the `spring-demo` application config**

`reactive-programming/project-reactor/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8094

upstream:
  base-url: http://localhost:8095
```

- [ ] **Step 13: Write the failing smoke test for `spring-demo`**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/ReactorDemoApplicationTest.groovy`:

```groovy
package com.testingai.reactor

import spock.lang.Specification

class ReactorDemoApplicationTest extends Specification {

    def "main class exists"() {
        expect:
        new ReactorDemoApplication()
    }
}
```

- [ ] **Step 14: Verify the test fails to compile**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test`
Expected: FAIL — `ReactorDemoApplication` does not exist (compilation error)

- [ ] **Step 15: Create the `spring-demo` application class**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/ReactorDemoApplication.java`:

```java
package com.testingai.reactor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorDemoApplication.class, args);
    }
}
```

- [ ] **Step 16: Verify the smoke test passes and the whole reactor builds**

Run:
```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming
mvn clean package
```
Expected: PASS — both modules build and their smoke tests pass.

- [ ] **Step 17: Wire `reactive-programming` into the pre-commit Spotless hook**

Modify `.githooks/pre-commit`. Change:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols)/.*\.java$' || true)
```

to:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming)/.*\.java$' || true)
```

and add, immediately after the `communication-protocols` block:

```bash
if echo "$STAGED_JAVA" | grep -q '^reactive-programming/'; then
    echo "[pre-commit] Applying Spotless formatting to staged reactive-programming Java files..."
    (cd "$ROOT/reactive-programming" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 18: Update `CLAUDE.md`**

Insert a new command section into `CLAUDE.md` immediately after the "### gRPC communication protocol demo" section (before "### Spring Boot starter demo"):

```markdown
### Project Reactor demo (run from the reactor root, no docker infrastructure required)

\`\`\`bash
cd reactive-programming

mvn clean package                                                  # build both apps (reactor build)
mvn test                                                            # unit tests for both modules (Gatling excluded automatically)
mvn test -pl project-reactor/spring-demo -Dtest=ClassName            # single test class
mvn -pl project-reactor/upstream-demo spring-boot:run                 # run the upstream service first (:8095)
mvn -pl project-reactor/spring-demo spring-boot:run                   # then the main demo app (:8094)
mvn gatling:test -pl project-reactor/spring-demo                       # load test — requires both apps running first
\`\`\`
```

(Write the actual triple-backtick fences, not escaped — the escaping above is only to nest this code block inside the plan document.)

Add a new row to the "Repository layout" table in `CLAUDE.md`, immediately after the `communication-protocols/grpc/{server-demo,client-demo}/` row:

```markdown
| `reactive-programming/project-reactor/{spring-demo,upstream-demo}/` | Project Reactor demo — two independent Spring Boot WebFlux apps covering Mono/Flux basics, backpressure/error handling, schedulers/concurrency, and SSE/WebClient streaming; `upstream-demo` must be started before `spring-demo`'s `streaming/upstream/*` endpoints work — no external infrastructure required |
```

- [ ] **Step 19: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming .githooks/pre-commit CLAUDE.md
git commit -m "feat(reactive-programming): scaffold project-reactor spring-demo and upstream-demo skeletons"
```

---

## Task 2: `upstream-demo` domain and `UpstreamController`

**Files:**
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/Product.java`
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/PriceTick.java`
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/SampleDataService.java`
- Create: `reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/controller/UpstreamController.java`
- Test: `reactive-programming/project-reactor/upstream-demo/src/test/groovy/com/testingai/reactor/upstream/controller/UpstreamControllerTest.groovy`

**Interfaces:**
- Consumes: nothing beyond `ReactorUpstreamDemoApplication`'s package root from Task 1.
- Produces: `com.testingai.reactor.upstream.domain.Product(String id, String name, long priceCents)`, `com.testingai.reactor.upstream.domain.PriceTick(String productId, long priceCents, Instant timestamp)`, `com.testingai.reactor.upstream.domain.SampleDataService#catalog(): List<Product>` — Task 6 (`StreamingServiceTest`, in `spring-demo`) mirrors this exact JSON shape via `spring-demo`'s own `Product`/`PriceTick` records, so field names/order here must stay `id, name, priceCents` and `productId, priceCents, timestamp`.

- [ ] **Step 1: Create the `Product` and `PriceTick` records**

`reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/Product.java`:

```java
package com.testingai.reactor.upstream.domain;

public record Product(String id, String name, long priceCents) {
}
```

`reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/PriceTick.java`:

```java
package com.testingai.reactor.upstream.domain;

import java.time.Instant;

public record PriceTick(String productId, long priceCents, Instant timestamp) {
}
```

- [ ] **Step 2: Create `SampleDataService`**

`reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/domain/SampleDataService.java`:

```java
package com.testingai.reactor.upstream.domain;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SampleDataService {

    private final List<Product> catalog = List.of(
            new Product("P-100", "Wireless Mouse", 2499),
            new Product("P-101", "Mechanical Keyboard", 8999),
            new Product("P-102", "USB-C Hub", 3499),
            new Product("P-103", "27-inch Monitor", 24999),
            new Product("P-104", "Webcam", 5499));

    public List<Product> catalog() {
        return catalog;
    }
}
```

- [ ] **Step 3: Write the failing controller test**

`reactive-programming/project-reactor/upstream-demo/src/test/groovy/com/testingai/reactor/upstream/controller/UpstreamControllerTest.groovy`:

```groovy
package com.testingai.reactor.upstream.controller

import com.testingai.reactor.upstream.domain.PriceTick
import com.testingai.reactor.upstream.domain.Product
import com.testingai.reactor.upstream.domain.SampleDataService
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier
import spock.lang.Specification

class UpstreamControllerTest extends Specification {

    def sampleDataService = new SampleDataService()
    def webTestClient = WebTestClient.bindToController(new UpstreamController(sampleDataService)).build()

    def "GET /upstream/products streams the full catalog"() {
        expect:
        webTestClient.get().uri("/upstream/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product)
                .hasSize(sampleDataService.catalog().size())
    }

    def "GET /upstream/ticks streams price ticks over time"() {
        given:
        def result = webTestClient.get().uri("/upstream/ticks")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(PriceTick)

        expect:
        StepVerifier.create(result.getResponseBody().take(2))
                .expectNextCount(2)
                .verifyComplete()
    }
}
```

- [ ] **Step 4: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/upstream-demo test`
Expected: FAIL — `UpstreamController` does not exist (compilation error)

- [ ] **Step 5: Create `UpstreamController`**

`reactive-programming/project-reactor/upstream-demo/src/main/java/com/testingai/reactor/upstream/controller/UpstreamController.java`:

```java
package com.testingai.reactor.upstream.controller;

import com.testingai.reactor.upstream.domain.PriceTick;
import com.testingai.reactor.upstream.domain.Product;
import com.testingai.reactor.upstream.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/upstream")
@RequiredArgsConstructor
public class UpstreamController {

    private static final Duration TICK_INTERVAL = Duration.ofMillis(500);
    private static final long MAX_PRICE_STEP_CENTS = 50;

    private final SampleDataService sampleDataService;

    @GetMapping(value = "/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Product> products() {
        return Flux.fromIterable(sampleDataService.catalog());
    }

    @GetMapping(value = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PriceTick>> ticks() {
        List<Product> catalog = sampleDataService.catalog();
        return Flux.interval(TICK_INTERVAL)
                .map(tick -> catalog.get((int) (tick % catalog.size())))
                .map(product -> ServerSentEvent.builder(randomWalk(product)).build());
    }

    private PriceTick randomWalk(Product product) {
        long deltaCents = ThreadLocalRandom.current().nextLong(-MAX_PRICE_STEP_CENTS, MAX_PRICE_STEP_CENTS + 1);
        long walkedPriceCents = Math.max(1, product.priceCents() + deltaCents);
        return new PriceTick(product.id(), walkedPriceCents, Instant.now());
    }
}
```

- [ ] **Step 6: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/upstream-demo test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/upstream-demo
git commit -m "feat(reactive-programming): add upstream-demo product feed and SSE tick endpoints"
```

---

## Task 3: `spring-demo` domain and `BasicsService`

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/Product.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/ProductWithDiscount.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/PriceTick.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/SampleDataService.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/basics/BasicsService.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/basics/BasicsServiceTest.groovy`

**Interfaces:**
- Consumes: nothing beyond `ReactorDemoApplication`'s package root from Task 1.
- Produces: `com.testingai.reactor.domain.Product(String id, String name, long priceCents)`, `com.testingai.reactor.domain.ProductWithDiscount(Product product, long discountedPriceCents)`, `com.testingai.reactor.domain.PriceTick(String productId, long priceCents, Instant timestamp)`, `com.testingai.reactor.domain.SampleDataService#catalog(): List<Product>` — reused by Tasks 4–7. `com.testingai.reactor.basics.BasicsService` with `allProducts(): Flux<Product>`, `productById(String id): Mono<Product>`, `generatedProducts(int count): Flux<Product>`, `discountedCatalog(): Flux<ProductWithDiscount>`, `combinedViaConcat(Flux<Product>, Flux<Product>): Flux<Product>`, `combinedViaMerge(Flux<Product>, Flux<Product>): Flux<Product>` — `combinedViaConcat`/`combinedViaMerge` are consumed only by this task's own test (no controller endpoint), demonstrating `Flux.concat`/`Flux.merge` ordering semantics.

- [ ] **Step 1: Create the `spring-demo` domain records**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/Product.java`:

```java
package com.testingai.reactor.domain;

public record Product(String id, String name, long priceCents) {
}
```

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/ProductWithDiscount.java`:

```java
package com.testingai.reactor.domain;

public record ProductWithDiscount(Product product, long discountedPriceCents) {
}
```

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/PriceTick.java`:

```java
package com.testingai.reactor.domain;

import java.time.Instant;

public record PriceTick(String productId, long priceCents, Instant timestamp) {
}
```

- [ ] **Step 2: Create `SampleDataService`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/domain/SampleDataService.java`:

```java
package com.testingai.reactor.domain;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SampleDataService {

    private final List<Product> catalog = List.of(
            new Product("P-100", "Wireless Mouse", 2499),
            new Product("P-101", "Mechanical Keyboard", 8999),
            new Product("P-102", "USB-C Hub", 3499),
            new Product("P-103", "27-inch Monitor", 24999),
            new Product("P-104", "Webcam", 5499));

    public List<Product> catalog() {
        return catalog;
    }
}
```

- [ ] **Step 3: Write the failing `BasicsService` test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/basics/BasicsServiceTest.groovy`:

```groovy
package com.testingai.reactor.basics

import com.testingai.reactor.domain.Product
import com.testingai.reactor.domain.SampleDataService
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import spock.lang.Specification

class BasicsServiceTest extends Specification {

    def sampleDataService = new SampleDataService()
    def basicsService = new BasicsService(sampleDataService)

    def "allProducts emits every catalog product"() {
        expect:
        StepVerifier.create(basicsService.allProducts())
                .expectNextCount(sampleDataService.catalog().size())
                .verifyComplete()
    }

    def "productById emits the matching product"() {
        expect:
        StepVerifier.create(basicsService.productById("P-101"))
                .expectNextMatches({ it.name() == "Mechanical Keyboard" })
                .verifyComplete()
    }

    def "productById completes empty for an unknown id"() {
        expect:
        StepVerifier.create(basicsService.productById("does-not-exist"))
                .verifyComplete()
    }

    def "generatedProducts emits exactly the requested count"() {
        expect:
        StepVerifier.create(basicsService.generatedProducts(7))
                .expectNextCount(7)
                .verifyComplete()
    }

    def "discountedCatalog applies the standard 10% discount to every product"() {
        expect:
        StepVerifier.create(basicsService.discountedCatalog())
                .thenConsumeWhile({ it.discountedPriceCents() == Math.round(it.product().priceCents() * 0.9) })
                .verifyComplete()
    }

    def "combinedViaConcat plays the first flux to completion before the second"() {
        given:
        def first = Flux.just(new Product("A", "A", 100))
        def second = Flux.just(new Product("B", "B", 200))

        expect:
        StepVerifier.create(basicsService.combinedViaConcat(first, second))
                .expectNextMatches({ it.id() == "A" })
                .expectNextMatches({ it.id() == "B" })
                .verifyComplete()
    }

    def "combinedViaMerge emits both sources without dropping any items"() {
        given:
        def first = Flux.just(new Product("A", "A", 100))
        def second = Flux.just(new Product("B", "B", 200))

        expect:
        StepVerifier.create(basicsService.combinedViaMerge(first, second))
                .recordWith({ [] as Set })
                .expectNextCount(2)
                .consumeRecordedWith({ ids -> assert ids*.id().toSet() == ["A", "B"].toSet() })
                .verifyComplete()
    }
}
```

- [ ] **Step 4: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=BasicsServiceTest`
Expected: FAIL — `BasicsService` does not exist (compilation error)

- [ ] **Step 5: Create `BasicsService`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/basics/BasicsService.java`:

```java
package com.testingai.reactor.basics;

import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.ProductWithDiscount;
import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BasicsService {

    private static final double STANDARD_DISCOUNT_RATE = 0.10;

    private final SampleDataService sampleDataService;

    public Flux<Product> allProducts() {
        return Flux.fromIterable(sampleDataService.catalog());
    }

    public Mono<Product> productById(String id) {
        return allProducts()
                .filter(product -> product.id().equals(id))
                .next();
    }

    public Flux<Product> generatedProducts(int count) {
        List<Product> catalog = sampleDataService.catalog();
        return Flux.generate(() -> 0, (index, sink) -> {
            if (index >= count) {
                sink.complete();
                return index;
            }
            Product source = catalog.get(index % catalog.size());
            sink.next(new Product(source.id() + "-" + index, source.name(), source.priceCents()));
            return index + 1;
        });
    }

    public Flux<ProductWithDiscount> discountedCatalog() {
        Flux<Double> discountRates = Flux.fromIterable(sampleDataService.catalog())
                .map(product -> STANDARD_DISCOUNT_RATE);
        return Flux.zip(allProducts(), discountRates, this::applyDiscount);
    }

    public Flux<Product> combinedViaConcat(Flux<Product> first, Flux<Product> second) {
        return Flux.concat(first, second);
    }

    public Flux<Product> combinedViaMerge(Flux<Product> first, Flux<Product> second) {
        return Flux.merge(first, second);
    }

    private ProductWithDiscount applyDiscount(Product product, double discountRate) {
        long discountedPriceCents = Math.round(product.priceCents() * (1 - discountRate));
        return new ProductWithDiscount(product, discountedPriceCents);
    }
}
```

- [ ] **Step 6: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=BasicsServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "feat(reactive-programming): add spring-demo domain model and BasicsService"
```

---

## Task 4: `ResilienceService` — backpressure, retry, timeout

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/FailureSimulator.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/BackpressureResultDto.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/ResilienceService.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/resilience/FailureSimulatorTest.groovy`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/resilience/ResilienceServiceTest.groovy`

**Interfaces:**
- Consumes: nothing from other packages (no constructor dependencies — `FailureSimulator` is a static utility).
- Produces: `com.testingai.reactor.resilience.BackpressureResultDto(String strategy, long emitted, long processed, long droppedOrBuffered)`, `com.testingai.reactor.resilience.ResilienceService` with `demonstrateBackpressure(String strategy): Mono<BackpressureResultDto>`, `retryDemo(): Flux<String>`, `timeoutDemo(): Mono<String>` — all consumed directly by `DemoController` in Task 7.

- [ ] **Step 1: Write the failing `FailureSimulator` test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/resilience/FailureSimulatorTest.groovy`:

```groovy
package com.testingai.reactor.resilience

import spock.lang.Specification

class FailureSimulatorTest extends Specification {

    def "maybeThrow does not throw most of the time"() {
        given:
        int failures = 0

        when:
        1000.times {
            try {
                FailureSimulator.maybeThrow("test")
            } catch (RuntimeException ignored) {
                failures++
            }
        }

        then:
        // With 5% failure rate, expect roughly 50 failures; accept 5-200 range
        failures >= 5 && failures <= 200
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=FailureSimulatorTest`
Expected: FAIL — `FailureSimulator` does not exist (compilation error)

- [ ] **Step 3: Create `FailureSimulator`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/FailureSimulator.java`:

```java
package com.testingai.reactor.resilience;

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

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=FailureSimulatorTest`
Expected: PASS

- [ ] **Step 5: Create `BackpressureResultDto`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/BackpressureResultDto.java`:

```java
package com.testingai.reactor.resilience;

public record BackpressureResultDto(String strategy, long emitted, long processed, long droppedOrBuffered) {
}
```

- [ ] **Step 6: Write the failing `ResilienceService` test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/resilience/ResilienceServiceTest.groovy`:

```groovy
package com.testingai.reactor.resilience

import reactor.test.StepVerifier
import spock.lang.Specification
import spock.lang.Unroll

class ResilienceServiceTest extends Specification {

    def resilienceService = new ResilienceService()

    @Unroll
    def "demonstrateBackpressure(#strategy) accounts for every emitted item and overflows the slow consumer"() {
        expect:
        StepVerifier.create(resilienceService.demonstrateBackpressure(strategy))
                .assertNext({ result ->
                    assert result.strategy() == strategy
                    assert result.emitted() == 200L
                    assert result.processed() + result.droppedOrBuffered() == 200L
                    assert result.droppedOrBuffered() > 0L
                })
                .verifyComplete()

        where:
        strategy << ["buffer", "drop"]
    }

    def "retryDemo resolves to success or the exhausted-retries fallback"() {
        expect:
        StepVerifier.create(resilienceService.retryDemo())
                .expectNextMatches({ it == "success" || it == "fallback-after-retries-exhausted" })
                .verifyComplete()
    }

    def "retryDemo resolves to success in the overwhelming majority of runs"() {
        given:
        int successes = 0

        when:
        50.times {
            if (resilienceService.retryDemo().blockFirst() == "success") {
                successes++
            }
        }

        then:
        successes >= 45
    }

    def "timeoutDemo returns the fallback once the simulated call exceeds the timeout"() {
        expect:
        StepVerifier.create(resilienceService.timeoutDemo())
                .expectNext("fallback-after-timeout")
                .verifyComplete()
    }
}
```

- [ ] **Step 7: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=ResilienceServiceTest`
Expected: FAIL — `ResilienceService` does not exist (compilation error)

- [ ] **Step 8: Create `ResilienceService`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/resilience/ResilienceService.java`:

```java
package com.testingai.reactor.resilience;

import org.springframework.stereotype.Service;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ResilienceService {

    private static final int FAST_PRODUCER_COUNT = 200;
    private static final int BUFFER_CAPACITY = 16;
    private static final Duration SLOW_CONSUMER_DELAY = Duration.ofMillis(5);
    private static final int RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(50);
    private static final Duration SLOW_CALL_DURATION = Duration.ofMillis(300);
    private static final Duration CALL_TIMEOUT = Duration.ofMillis(100);

    public Mono<BackpressureResultDto> demonstrateBackpressure(String strategy) {
        AtomicLong processed = new AtomicLong();
        AtomicLong droppedOrBuffered = new AtomicLong();

        Flux<Integer> fastProducer = Flux.range(0, FAST_PRODUCER_COUNT);

        Flux<Integer> withStrategy = "drop".equals(strategy)
                ? fastProducer.onBackpressureDrop(dropped -> droppedOrBuffered.incrementAndGet())
                : fastProducer.onBackpressureBuffer(BUFFER_CAPACITY, buffered -> droppedOrBuffered.incrementAndGet(),
                        BufferOverflowStrategy.DROP_OLDEST);

        return withStrategy
                .limitRate(1)
                .delayElements(SLOW_CONSUMER_DELAY)
                .doOnNext(item -> processed.incrementAndGet())
                .then(Mono.fromSupplier(() -> new BackpressureResultDto(strategy, FAST_PRODUCER_COUNT, processed.get(),
                        droppedOrBuffered.get())));
    }

    public Flux<String> retryDemo() {
        return Mono.<String>fromRunnable(() -> FailureSimulator.maybeThrow("retryDemo"))
                .thenReturn("success")
                .retryWhen(Retry.backoff(RETRY_ATTEMPTS, RETRY_BACKOFF))
                .onErrorReturn("fallback-after-retries-exhausted")
                .flux();
    }

    public Mono<String> timeoutDemo() {
        return Mono.delay(SLOW_CALL_DURATION)
                .thenReturn("slow-response")
                .timeout(CALL_TIMEOUT)
                .onErrorResume(TimeoutException.class, e -> Mono.just("fallback-after-timeout"));
    }
}
```

- [ ] **Step 9: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=ResilienceServiceTest`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "feat(reactive-programming): add ResilienceService for backpressure, retry, and timeout patterns"
```

---

## Task 5: `ConcurrencyService` — schedulers

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/concurrency/ThreadTraceDto.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/concurrency/ConcurrencyService.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/concurrency/ConcurrencyServiceTest.groovy`

**Interfaces:**
- Consumes: `com.testingai.reactor.domain.SampleDataService#catalog(): List<Product>` (Task 3).
- Produces: `com.testingai.reactor.concurrency.ThreadTraceDto(String stage, String threadName)`, `com.testingai.reactor.concurrency.ConcurrencyService` with `subscribeOnVsPublishOn(): Mono<List<ThreadTraceDto>>`, `parallelDemo(): Mono<List<ThreadTraceDto>>`, `blockingOffload(): Mono<String>` — consumed by `DemoController` in Task 7.

- [ ] **Step 1: Create `ThreadTraceDto`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/concurrency/ThreadTraceDto.java`:

```java
package com.testingai.reactor.concurrency;

public record ThreadTraceDto(String stage, String threadName) {
}
```

- [ ] **Step 2: Write the failing `ConcurrencyService` test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/concurrency/ConcurrencyServiceTest.groovy`:

```groovy
package com.testingai.reactor.concurrency

import com.testingai.reactor.domain.SampleDataService
import reactor.test.StepVerifier
import spock.lang.Specification

class ConcurrencyServiceTest extends Specification {

    def sampleDataService = new SampleDataService()
    def concurrencyService = new ConcurrencyService(sampleDataService)

    def "subscribeOnVsPublishOn records one trace per stage, both off the calling thread"() {
        given:
        def callingThreadName = Thread.currentThread().getName()

        expect:
        StepVerifier.create(concurrencyService.subscribeOnVsPublishOn())
                .assertNext({ traces ->
                    assert traces.size() == 2
                    assert traces*.stage().toSet() == ["subscribeOn", "publishOn"].toSet()
                    assert traces*.threadName().every { it != callingThreadName }
                })
                .verifyComplete()
    }

    def "parallelDemo records one thread trace per catalog product"() {
        expect:
        StepVerifier.create(concurrencyService.parallelDemo())
                .assertNext({ traces -> assert traces.size() == sampleDataService.catalog().size() })
                .verifyComplete()
    }

    def "blockingOffload runs the blocking call on a boundedElastic thread"() {
        expect:
        StepVerifier.create(concurrencyService.blockingOffload())
                .assertNext({ threadName -> assert threadName.contains("boundedElastic") })
                .verifyComplete()
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=ConcurrencyServiceTest`
Expected: FAIL — `ConcurrencyService` does not exist (compilation error)

- [ ] **Step 4: Create `ConcurrencyService`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/concurrency/ConcurrencyService.java`:

```java
package com.testingai.reactor.concurrency;

import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class ConcurrencyService {

    private static final Duration BLOCKING_CALL_DURATION = Duration.ofMillis(50);

    private final SampleDataService sampleDataService;

    public Mono<List<ThreadTraceDto>> subscribeOnVsPublishOn() {
        Mono<ThreadTraceDto> subscribeOnTrace = Mono
                .fromSupplier(() -> new ThreadTraceDto("subscribeOn", Thread.currentThread().getName()))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<ThreadTraceDto> publishOnTrace = Mono.just("assembly")
                .publishOn(Schedulers.parallel())
                .map(ignored -> new ThreadTraceDto("publishOn", Thread.currentThread().getName()));

        return Flux.merge(subscribeOnTrace, publishOnTrace).collectList();
    }

    public Mono<List<ThreadTraceDto>> parallelDemo() {
        List<ThreadTraceDto> traces = new CopyOnWriteArrayList<>();
        return Flux.fromIterable(sampleDataService.catalog())
                .parallel(4)
                .runOn(Schedulers.parallel())
                .doOnNext(product -> traces.add(new ThreadTraceDto(product.id(), Thread.currentThread().getName())))
                .sequential()
                .then(Mono.fromSupplier(() -> List.copyOf(traces)));
    }

    public Mono<String> blockingOffload() {
        return Mono.fromCallable(this::simulateBlockingCall)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String simulateBlockingCall() {
        try {
            Thread.sleep(BLOCKING_CALL_DURATION.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Thread.currentThread().getName();
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=ConcurrencyServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "feat(reactive-programming): add ConcurrencyService for subscribeOn/publishOn and parallel scheduling"
```

---

## Task 6: `StreamingService` and `WebClientConfig`

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/streaming/WebClientConfig.java`
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/streaming/StreamingService.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/streaming/StreamingServiceTest.groovy`

**Interfaces:**
- Consumes: `com.testingai.reactor.domain.SampleDataService#catalog(): List<Product>`, `com.testingai.reactor.domain.Product`, `com.testingai.reactor.domain.PriceTick` (Task 3).
- Produces: `com.testingai.reactor.streaming.StreamingService` with `localTicks(): Flux<ServerSentEvent<PriceTick>>`, `fetchUpstreamProducts(): Flux<Product>`, `relayUpstreamTicks(): Flux<PriceTick>` — consumed by `DemoController` in Task 7. `WebClientConfig` produces a `WebClient` bean (name `upstreamWebClient`) that `StreamingService` takes as a constructor argument.

- [ ] **Step 1: Create `WebClientConfig`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/streaming/WebClientConfig.java`:

```java
package com.testingai.reactor.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient upstreamWebClient(@Value("${upstream.base-url}") String upstreamBaseUrl) {
        return WebClient.builder().baseUrl(upstreamBaseUrl).build();
    }
}
```

- [ ] **Step 2: Write the failing `StreamingService` test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/streaming/StreamingServiceTest.groovy`:

```groovy
package com.testingai.reactor.streaming

import com.testingai.reactor.domain.SampleDataService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import spock.lang.Specification

class StreamingServiceTest extends Specification {

    MockWebServer mockWebServer = new MockWebServer()
    StreamingService streamingService

    def setup() {
        mockWebServer.start()
        def webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build()
        streamingService = new StreamingService(new SampleDataService(), webClient)
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    def "fetchUpstreamProducts deserializes the upstream NDJSON product stream"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody('{"id":"P-100","name":"Wireless Mouse","priceCents":2499}\n' +
                        '{"id":"P-101","name":"Mechanical Keyboard","priceCents":8999}\n')
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))

        expect:
        StepVerifier.create(streamingService.fetchUpstreamProducts())
                .expectNextMatches({ it.id() == "P-100" })
                .expectNextMatches({ it.id() == "P-101" })
                .verifyComplete()
    }

    def "relayUpstreamTicks unwraps the upstream SSE payload into a PriceTick"() {
        given:
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody('data:{"productId":"P-100","priceCents":2510,"timestamp":"2026-07-28T10:00:00Z"}\n\n')
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))

        expect:
        StepVerifier.create(streamingService.relayUpstreamTicks())
                .expectNextMatches({ it.productId() == "P-100" && it.priceCents() == 2510L })
                .verifyComplete()
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=StreamingServiceTest`
Expected: FAIL — `StreamingService` does not exist (compilation error)

- [ ] **Step 4: Create `StreamingService`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/streaming/StreamingService.java`:

```java
package com.testingai.reactor.streaming;

import com.testingai.reactor.domain.PriceTick;
import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class StreamingService {

    private static final Duration TICK_INTERVAL = Duration.ofMillis(500);
    private static final long MAX_PRICE_STEP_CENTS = 50;

    private final SampleDataService sampleDataService;
    private final WebClient upstreamWebClient;

    public Flux<ServerSentEvent<PriceTick>> localTicks() {
        List<Product> catalog = sampleDataService.catalog();
        return Flux.interval(TICK_INTERVAL)
                .map(tick -> catalog.get((int) (tick % catalog.size())))
                .map(product -> ServerSentEvent.builder(randomWalk(product)).build());
    }

    public Flux<Product> fetchUpstreamProducts() {
        return upstreamWebClient.get()
                .uri("/upstream/products")
                .retrieve()
                .bodyToFlux(Product.class);
    }

    public Flux<PriceTick> relayUpstreamTicks() {
        return upstreamWebClient.get()
                .uri("/upstream/ticks")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<PriceTick>>() {
                })
                .map(ServerSentEvent::data);
    }

    private PriceTick randomWalk(Product product) {
        long deltaCents = ThreadLocalRandom.current().nextLong(-MAX_PRICE_STEP_CENTS, MAX_PRICE_STEP_CENTS + 1);
        long walkedPriceCents = Math.max(1, product.priceCents() + deltaCents);
        return new PriceTick(product.id(), walkedPriceCents, Instant.now());
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=StreamingServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "feat(reactive-programming): add StreamingService for local SSE and upstream WebClient consumption"
```

---

## Task 7: `DemoController`

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/controller/DemoController.java`
- Test: `reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/controller/DemoControllerTest.groovy`

**Interfaces:**
- Consumes: `BasicsService` (Task 3), `ResilienceService` (Task 4), `ConcurrencyService` (Task 5), `StreamingService` (Task 6) — all four injected via constructor, in that order.
- Produces: the full `/demo/**` REST surface listed in the design spec's endpoint table — this is the last piece; no later task depends on `DemoController`.

- [ ] **Step 1: Write the failing controller test**

`reactive-programming/project-reactor/spring-demo/src/test/groovy/com/testingai/reactor/controller/DemoControllerTest.groovy`:

```groovy
package com.testingai.reactor.controller

import com.testingai.reactor.basics.BasicsService
import com.testingai.reactor.concurrency.ConcurrencyService
import com.testingai.reactor.domain.Product
import com.testingai.reactor.resilience.BackpressureResultDto
import com.testingai.reactor.resilience.ResilienceService
import com.testingai.reactor.streaming.StreamingService
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class DemoControllerTest extends Specification {

    def basicsService = Mock(BasicsService)
    def resilienceService = Mock(ResilienceService)
    def concurrencyService = Mock(ConcurrencyService)
    def streamingService = Mock(StreamingService)

    def webTestClient = WebTestClient.bindToController(
            new DemoController(basicsService, resilienceService, concurrencyService, streamingService))
            .build()

    def "GET /demo/basics/products/{id} returns 200 with the product when found"() {
        given:
        basicsService.productById("P-100") >> Mono.just(new Product("P-100", "Wireless Mouse", 2499))

        expect:
        webTestClient.get().uri("/demo/basics/products/P-100")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product)
                .isEqualTo(new Product("P-100", "Wireless Mouse", 2499))
    }

    def "GET /demo/basics/products/{id} returns 404 when the product is unknown"() {
        given:
        basicsService.productById("missing") >> Mono.empty()

        expect:
        webTestClient.get().uri("/demo/basics/products/missing")
                .exchange()
                .expectStatus().isNotFound()
    }

    def "GET /demo/resilience/backpressure returns the backpressure result"() {
        given:
        resilienceService.demonstrateBackpressure("drop") >> Mono.just(new BackpressureResultDto("drop", 200, 5, 195))

        expect:
        webTestClient.get().uri("/demo/resilience/backpressure?strategy=drop")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BackpressureResultDto)
                .isEqualTo(new BackpressureResultDto("drop", 200, 5, 195))
    }

    def "GET /demo/concurrency/blocking-offload returns the offloaded thread name"() {
        given:
        concurrencyService.blockingOffload() >> Mono.just("boundedElastic-1")

        expect:
        webTestClient.get().uri("/demo/concurrency/blocking-offload")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String)
                .isEqualTo("boundedElastic-1")
    }

    def "GET /demo/streaming/upstream/products streams products from the streaming service"() {
        given:
        streamingService.fetchUpstreamProducts() >> Flux.just(new Product("P-100", "Wireless Mouse", 2499))

        expect:
        webTestClient.get().uri("/demo/streaming/upstream/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product)
                .isEqualTo([new Product("P-100", "Wireless Mouse", 2499)])
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController` does not exist (compilation error)

- [ ] **Step 3: Create `DemoController`**

`reactive-programming/project-reactor/spring-demo/src/main/java/com/testingai/reactor/controller/DemoController.java`:

```java
package com.testingai.reactor.controller;

import com.testingai.reactor.basics.BasicsService;
import com.testingai.reactor.concurrency.ConcurrencyService;
import com.testingai.reactor.concurrency.ThreadTraceDto;
import com.testingai.reactor.domain.PriceTick;
import com.testingai.reactor.domain.Product;
import com.testingai.reactor.domain.ProductWithDiscount;
import com.testingai.reactor.resilience.BackpressureResultDto;
import com.testingai.reactor.resilience.ResilienceService;
import com.testingai.reactor.streaming.StreamingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final BasicsService basicsService;
    private final ResilienceService resilienceService;
    private final ConcurrencyService concurrencyService;
    private final StreamingService streamingService;

    @GetMapping(value = "/basics/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Product> basicsProducts() {
        return basicsService.allProducts();
    }

    @GetMapping("/basics/products/{id}")
    public Mono<ResponseEntity<Product>> basicsProductById(@PathVariable String id) {
        return basicsService.productById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/basics/generated", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Product> basicsGenerated(@RequestParam int count) {
        return basicsService.generatedProducts(count);
    }

    @GetMapping(value = "/basics/discounted", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ProductWithDiscount> basicsDiscounted() {
        return basicsService.discountedCatalog();
    }

    @GetMapping("/resilience/backpressure")
    public Mono<BackpressureResultDto> resilienceBackpressure(@RequestParam String strategy) {
        return resilienceService.demonstrateBackpressure(strategy);
    }

    @GetMapping(value = "/resilience/retry", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> resilienceRetry() {
        return resilienceService.retryDemo();
    }

    @GetMapping("/resilience/timeout")
    public Mono<String> resilienceTimeout() {
        return resilienceService.timeoutDemo();
    }

    @GetMapping("/concurrency/subscribe-vs-publish-on")
    public Mono<List<ThreadTraceDto>> concurrencySubscribeVsPublishOn() {
        return concurrencyService.subscribeOnVsPublishOn();
    }

    @GetMapping("/concurrency/parallel")
    public Mono<List<ThreadTraceDto>> concurrencyParallel() {
        return concurrencyService.parallelDemo();
    }

    @GetMapping("/concurrency/blocking-offload")
    public Mono<String> concurrencyBlockingOffload() {
        return concurrencyService.blockingOffload();
    }

    @GetMapping(value = "/streaming/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PriceTick>> streamingTicks() {
        return streamingService.localTicks();
    }

    @GetMapping(value = "/streaming/upstream/products", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Product> streamingUpstreamProducts() {
        return streamingService.fetchUpstreamProducts();
    }

    @GetMapping(value = "/streaming/upstream/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PriceTick> streamingUpstreamTicks() {
        return streamingService.relayUpstreamTicks();
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test -Dtest=DemoControllerTest`
Expected: PASS

- [ ] **Step 5: Run the full `spring-demo` test suite**

Run: `cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming && mvn -pl project-reactor/spring-demo test`
Expected: PASS (all specs from Tasks 3–7)

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "feat(reactive-programming): add DemoController exposing the full Project Reactor pattern surface"
```

---

## Task 8: Gatling load test

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/src/test/java/com/testingai/reactor/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the `/demo/**` endpoints from Task 7 (HTTP only, no compile-time dependency on the controller).
- Produces: nothing consumed by other tasks — this is a leaf artifact, excluded from `mvn test` by the inherited surefire `**/performance/**` exclude.

**Scope note:** the two SSE endpoints (`/demo/streaming/ticks`, `/demo/streaming/upstream/ticks`) are `Flux.interval`-backed streams that never complete. No module in this repo currently uses Gatling's dedicated SSE DSL, and a plain `http().get(...)` request against an endpoint that never completes its body would hang until `maxDuration`. This simulation therefore covers every endpoint **except** those two; the module README's `curl -N` walkthrough (Task 9) covers them manually.

- [ ] **Step 1: Create `DemoSimulation`**

`reactive-programming/project-reactor/spring-demo/src/test/java/com/testingai/reactor/performance/DemoSimulation.java`:

```java
package com.testingai.reactor.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8094");

    private final ScenarioBuilder demoScenario = scenario("Project Reactor Demo")
            .exec(http("Basics - All Products").get("/demo/basics/products").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Basics - Product By Id").get("/demo/basics/products/P-100").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Basics - Generated").get("/demo/basics/generated?count=5").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Basics - Discounted").get("/demo/basics/discounted").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Resilience - Backpressure Buffer").get("/demo/resilience/backpressure?strategy=buffer")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Resilience - Backpressure Drop").get("/demo/resilience/backpressure?strategy=drop")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Resilience - Retry").get("/demo/resilience/retry").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Resilience - Timeout").get("/demo/resilience/timeout").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Concurrency - Subscribe vs Publish On").get("/demo/concurrency/subscribe-vs-publish-on")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Concurrency - Parallel").get("/demo/concurrency/parallel").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Concurrency - Blocking Offload").get("/demo/concurrency/blocking-offload")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("Streaming - Upstream Products").get("/demo/streaming/upstream/products")
                    .check(status().is(200)));

    {
        setUp(demoScenario.injectOpen(rampUsers(2).during(Duration.ofSeconds(6)))).protocols(httpProtocol)
                .maxDuration(Duration.ofSeconds(60));
    }
}
```

- [ ] **Step 2: Verify the module still compiles with the Gatling sim present and excluded from `mvn test`**

Run:
```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming
mvn -pl project-reactor/spring-demo test
```
Expected: PASS — `DemoSimulation` is excluded by the surefire `**/performance/**` pattern, so it does not run, but the module (including the test-source tree) still compiles.

- [ ] **Step 3: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo
git commit -m "test(reactive-programming): add Gatling load test for spring-demo endpoints"
```

---

## Task 9: Module READMEs and final verification

**Files:**
- Create: `reactive-programming/project-reactor/spring-demo/README.md`
- Create: `reactive-programming/project-reactor/upstream-demo/README.md`

**Interfaces:**
- Consumes: the endpoint surface from Tasks 2 and 7 (documentation only, no code dependency).
- Produces: nothing consumed by other tasks — final task in the plan.

- [ ] **Step 1: Create the `spring-demo` README**

`reactive-programming/project-reactor/spring-demo/README.md`:

```markdown
# Project Reactor Demo — spring-demo

The primary Project Reactor demo app. See [`../README.md`](../README.md) for the concept/endpoint overview and [`../upstream-demo/README.md`](../upstream-demo/README.md) for the companion service.

## Prerequisites

- Java 21 (`JAVA_HOME` must point at a JDK 21 install — Groovy/Spock test compilation fails under newer JDKs)
- Maven
- No Docker

## Running

```bash
cd reactive-programming
mvn -pl project-reactor/spring-demo spring-boot:run
```

Listens on `:8094`. The `streaming/upstream/*` endpoints additionally require `upstream-demo` running on `:8095` — see [`../upstream-demo/README.md`](../upstream-demo/README.md).

## Trying it out

```bash
# Basics
curl http://localhost:8094/demo/basics/products
curl http://localhost:8094/demo/basics/products/P-100
curl "http://localhost:8094/demo/basics/generated?count=3"
curl http://localhost:8094/demo/basics/discounted

# Resilience — repeat until the 5% FailureSimulator trips to see the fallback path
curl "http://localhost:8094/demo/resilience/backpressure?strategy=drop"
curl "http://localhost:8094/demo/resilience/backpressure?strategy=buffer"
for i in $(seq 1 20); do curl http://localhost:8094/demo/resilience/retry; echo; done
curl http://localhost:8094/demo/resilience/timeout

# Concurrency
curl http://localhost:8094/demo/concurrency/subscribe-vs-publish-on
curl http://localhost:8094/demo/concurrency/parallel
curl http://localhost:8094/demo/concurrency/blocking-offload

# Streaming — local SSE feed (Ctrl-C to stop)
curl -N http://localhost:8094/demo/streaming/ticks

# Streaming via upstream-demo (requires upstream-demo running on :8095)
curl http://localhost:8094/demo/streaming/upstream/products
curl -N http://localhost:8094/demo/streaming/upstream/ticks
```

## Build & test

```bash
cd reactive-programming
mvn -pl project-reactor/spring-demo test                    # unit tests (Gatling excluded automatically)
mvn -pl project-reactor/spring-demo test -Dtest=ClassName    # single test class

# Gatling needs both apps running first
mvn -pl project-reactor/upstream-demo spring-boot:run &
mvn -pl project-reactor/spring-demo spring-boot:run &
mvn gatling:test -pl project-reactor/spring-demo
```
```

- [ ] **Step 2: Create the `upstream-demo` README**

`reactive-programming/project-reactor/upstream-demo/README.md`:

```markdown
# Project Reactor Demo — upstream-demo

A small standalone WebFlux service that `spring-demo`'s `WebClient` calls to demonstrate reactive HTTP streaming to another service. See [`../README.md`](../README.md) for the full module overview.

## Prerequisites

- Java 21 (`JAVA_HOME` must point at a JDK 21 install — Groovy/Spock test compilation fails under newer JDKs)
- Maven
- No Docker

## Running

```bash
cd reactive-programming
mvn -pl project-reactor/upstream-demo spring-boot:run
```

Listens on `:8095`.

## Trying it out

```bash
curl http://localhost:8095/upstream/products
curl -N http://localhost:8095/upstream/ticks   # SSE — Ctrl-C to stop
```

## Build & test

```bash
cd reactive-programming
mvn -pl project-reactor/upstream-demo test
mvn -pl project-reactor/upstream-demo test -Dtest=ClassName
```
```

- [ ] **Step 3: Run the full reactor build**

Run:
```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming
mvn clean package
```
Expected: PASS — both modules build, all unit tests pass, Gatling sim excluded.

- [ ] **Step 4: Apply Spotless formatting**

Run:
```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming
mvn spotless:apply
```
Expected: completes without error; re-run `mvn clean package` afterward if any files were reformatted, to confirm the build still passes.

- [ ] **Step 5: Manually smoke-test both apps end-to-end**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy/reactive-programming
mvn -pl project-reactor/upstream-demo spring-boot:run &
sleep 5
mvn -pl project-reactor/spring-demo spring-boot:run &
sleep 8

curl -s http://localhost:8095/upstream/products
curl -s http://localhost:8094/demo/basics/products
curl -s http://localhost:8094/demo/streaming/upstream/products
curl -s "http://localhost:8094/demo/resilience/backpressure?strategy=drop"
curl -s http://localhost:8094/demo/concurrency/blocking-offload
curl -s -N --max-time 2 http://localhost:8094/demo/streaming/ticks

kill %1 %2
```

Expected: every `curl` returns a `200`-shaped JSON/NDJSON/SSE body; the last command streams at least one `data:` event before the 2-second timeout kills it.

- [ ] **Step 6: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add reactive-programming/project-reactor/spring-demo/README.md reactive-programming/project-reactor/upstream-demo/README.md
git commit -m "docs(reactive-programming): add spring-demo and upstream-demo module READMEs"
```
