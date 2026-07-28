# gRPC Communication Protocol Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `communication-protocols/grpc/` category to this repo containing two independent Spring Boot apps — `server-demo` and `client-demo` — that demonstrate all four gRPC RPC patterns (unary, server streaming, client streaming, bidirectional streaming) against a small product-catalog/order domain.

**Architecture:** `server-demo` owns the `.proto` contract and implements `ProductCatalogService` (`net.devh:grpc-server-spring-boot-starter`). `client-demo` generates its own copy of the same generated stub classes (by pointing its own `protobuf-maven-plugin` at `server-demo`'s `.proto` file via a relative `protoSourceRoot` — no Maven dependency between the two modules) and exposes a REST `DemoController` that drives each RPC pattern via `net.devh:grpc-client-spring-boot-starter`-injected stubs, mapping gRPC errors to HTTP status codes.

**Tech Stack:** Spring Boot 3.4.4, Java 21, `net.devh:grpc-{server,client}-spring-boot-starter:3.1.0.RELEASE` (pulls `io.grpc:*:1.63.0` transitively), `org.xolstice.maven.plugins:protobuf-maven-plugin:0.6.1` + `kr.motd.maven:os-maven-plugin:1.7.1` for codegen, Lombok, JUnit 5 + Mockito + AssertJ, Gatling.

Design spec: `docs/superpowers/specs/2026-07-28-grpc-communication-protocol-demo-design.md`

## Global Constraints

- Java 21 (`maven.compiler.release=21`); Spring Boot parent `3.4.4` (matches every other module in this repo).
- All new instance fields assigned once and never reassigned must be `private final` (`.claude/rules/code-review.md`). Fields that are mutated (e.g. running totals in an anonymous `StreamObserver`) are exempt but must still be `private`.
- Prefer modern Java 17/21 idioms on all new code: records for data carriers, switch expressions, pattern matching — no boilerplate POJOs, no multi-line `switch` with `break`.
- Never call `.toString()` explicitly when a value is passed to an SLF4J `{}` placeholder or any implicit-`toString()` context.
- `InterruptedException` must be caught and the interrupt flag restored (`Thread.currentThread().interrupt()`), never declared via `throws`, when it can't meaningfully propagate to a caller (this applies to the REST controller methods in this plan, which block on a `CountDownLatch`).
- Plain JUnit 5 + Mockito + AssertJ only — no Spock/Groovy. Tabs for indentation (width 4), matching `distributed-transactions/saga` and `spring-boot-starters`.
- `FailureSimulator`: a `FAILURE_RATE = 0.05` constant and a static `maybeThrow(String context)` throwing `RuntimeException` — never a `shouldFail(): boolean` variant (`.claude/rules/code-review.md`).
- No Docker / external infrastructure for this category — everything runs via `mvn spring-boot:run`.
- Plaintext gRPC only — no TLS, matching the demo scope of the rest of the repo.
- Ports: `server-demo` → gRPC `9090`, HTTP `9091` (actuator health only). `client-demo` → HTTP `8091`.
- Redundant `throws` clauses are not permitted — only declare a checked exception if the method body can actually throw it.

---

## File Structure

```
communication-protocols/
├── pom.xml                                                    (new reactor parent)
├── eclipse-formatter.xml                                      (copy of distributed-transactions/eclipse-formatter.xml)
├── README.md                                                  (category index)
└── grpc/
    ├── README.md                                              (protocol overview)
    ├── server-demo/
    │   ├── pom.xml
    │   ├── README.md
    │   └── src/
    │       ├── main/
    │       │   ├── proto/catalog.proto                        (the shared contract — canonical copy)
    │       │   ├── java/com/testingai/grpc/server/
    │       │   │   ├── GrpcServerDemoApplication.java
    │       │   │   ├── domain/SampleDataService.java
    │       │   │   ├── service/ProductCatalogServiceImpl.java
    │       │   │   └── util/FailureSimulator.java
    │       │   └── resources/application.yml
    │       └── test/java/com/testingai/grpc/server/
    │           ├── GrpcServerDemoApplicationTest.java
    │           ├── domain/SampleDataServiceTest.java
    │           ├── service/ProductCatalogServiceImplTest.java
    │           └── util/FailureSimulatorTest.java
    └── client-demo/
        ├── pom.xml
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/grpc/client/
            │   │   ├── GrpcClientDemoApplication.java
            │   │   ├── config/GrpcStubConfig.java
            │   │   └── controller/
            │   │       ├── DemoController.java
            │   │       ├── DemoExceptionHandler.java
            │   │       └── dto/
            │   │           ├── ProductDto.java
            │   │           ├── OrderRequestDto.java
            │   │           ├── OrderSummaryDto.java
            │   │           └── OrderStatusUpdateDto.java
            │   └── resources/application.yml
            └── test/java/com/testingai/grpc/client/
                ├── GrpcClientDemoApplicationTest.java
                ├── controller/
                │   ├── DemoControllerTest.java
                │   └── DemoIntegrationTest.java
                ├── support/FakeProductCatalogService.java
                └── performance/DemoSimulation.java
```

`server-demo/src/main/proto/catalog.proto` declares `option java_package = "com.testingai.grpc.proto";` — a neutral package name (not `...server.proto`), since the contract is shared by both apps even though the canonical source file physically lives under `server-demo`. `client-demo`'s own `protobuf-maven-plugin` regenerates the identical classes into its own `target/generated-sources` by pointing `protoSourceRoot` at `../server-demo/src/main/proto` — there is no Maven `<dependency>` between the two modules; they are independently buildable.

Files not modified until the final task: `.githooks/pre-commit`, `CLAUDE.md`.

---

### Task 1: Scaffold the `communication-protocols` reactor and both module skeletons

**Files:**
- Create: `communication-protocols/pom.xml`
- Create: `communication-protocols/eclipse-formatter.xml`
- Create: `communication-protocols/grpc/server-demo/pom.xml`
- Create: `communication-protocols/grpc/server-demo/src/main/proto/catalog.proto`
- Create: `communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/GrpcServerDemoApplication.java`
- Create: `communication-protocols/grpc/server-demo/src/main/resources/application.yml`
- Create: `communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/GrpcServerDemoApplicationTest.java`
- Create: `communication-protocols/grpc/client-demo/pom.xml`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/GrpcClientDemoApplication.java`
- Create: `communication-protocols/grpc/client-demo/src/main/resources/application.yml`
- Create: `communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/GrpcClientDemoApplicationTest.java`

**Interfaces:**
- Produces: generated classes `com.testingai.grpc.proto.{ProductCatalogServiceGrpc, ProductRequest, ProductResponse, ListProductsRequest, OrderRequest, OrderSummary, OrderStatusUpdate}` — regenerated independently into each module's own `target/generated-sources/protobuf/{java,grpc-java}` on every build. All later tasks import from this package.
- Produces: `com.testingai.grpc.server.GrpcServerDemoApplication` (gRPC port `9090`, HTTP `9091`).
- Produces: `com.testingai.grpc.client.GrpcClientDemoApplication` (HTTP port `8091`, configured to reach the server at `static://localhost:9090`).

- [ ] **Step 1: Create the reactor parent POM**

`communication-protocols/pom.xml`:

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
    <artifactId>communication-protocols</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Communication Protocols</name>
    <description>Parent POM for all communication-protocol demo modules</description>

    <modules>
        <module>grpc/server-demo</module>
        <module>grpc/client-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <springdoc.version>2.8.6</springdoc.version>
        <grpc-spring-boot-starter.version>3.1.0.RELEASE</grpc-spring-boot-starter.version>
        <grpc.version>1.63.0</grpc.version>
        <protobuf.version>3.25.1</protobuf.version>
        <protobuf-maven-plugin.version>0.6.1</protobuf-maven-plugin.version>
        <os-maven-plugin.version>1.7.1</os-maven-plugin.version>
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

- [ ] **Step 2: Copy the Eclipse formatter config**

```bash
cp distributed-transactions/eclipse-formatter.xml communication-protocols/eclipse-formatter.xml
```

- [ ] **Step 3: Write the shared proto contract**

`communication-protocols/grpc/server-demo/src/main/proto/catalog.proto`:

```protobuf
syntax = "proto3";

package catalog;

option java_package = "com.testingai.grpc.proto";
option java_multiple_files = true;

service ProductCatalogService {
  rpc GetProduct(ProductRequest) returns (ProductResponse);
  rpc ListProducts(ListProductsRequest) returns (stream ProductResponse);
  rpc UploadOrders(stream OrderRequest) returns (OrderSummary);
  rpc StreamOrderStatus(stream OrderStatusUpdate) returns (stream OrderStatusUpdate);
}

message ProductRequest {
  string product_id = 1;
}

message ProductResponse {
  string product_id = 1;
  string name = 2;
  int64 price_cents = 3;
}

message ListProductsRequest {}

message OrderRequest {
  string product_id = 1;
  int32 quantity = 2;
}

message OrderSummary {
  int32 order_count = 1;
  int64 total_price_cents = 2;
}

message OrderStatusUpdate {
  string order_id = 1;
  string status = 2;
}
```

- [ ] **Step 4: Create the server-demo POM**

`communication-protocols/grpc/server-demo/pom.xml`:

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

    <artifactId>grpc-server-demo</artifactId>
    <name>gRPC Server Demo</name>
    <description>Learning and demonstration project for gRPC server-side RPC patterns</description>

    <dependencies>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
            <version>${grpc-spring-boot-starter.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>${os-maven-plugin.version}</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>${protobuf-maven-plugin.version}</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.grpc.server.GrpcServerDemoApplication</mainClass>
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

- [ ] **Step 5: Create the server application class and config**

`communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/GrpcServerDemoApplication.java`:

```java
package com.testingai.grpc.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GrpcServerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerDemoApplication.class, args);
	}
}
```

`communication-protocols/grpc/server-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 9091

grpc:
  server:
    port: 9090

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 6: Write the server context-load test**

`communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/GrpcServerDemoApplicationTest.java`:

```java
package com.testingai.grpc.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GrpcServerDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 7: Create the client-demo POM**

`communication-protocols/grpc/client-demo/pom.xml`:

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

    <artifactId>grpc-client-demo</artifactId>
    <name>gRPC Client Demo</name>
    <description>REST facade that drives all four gRPC RPC patterns against grpc-server-demo</description>

    <dependencies>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
            <version>${grpc-spring-boot-starter.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>${gatling.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>${os-maven-plugin.version}</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>${protobuf-maven-plugin.version}</version>
                <configuration>
                    <protoSourceRoot>${project.basedir}/../server-demo/src/main/proto</protoSourceRoot>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.grpc.client.GrpcClientDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.grpc.client.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Note: `io.grpc:grpc-inprocess` (needed later for `InProcessServerBuilder`/`InProcessChannelBuilder` in the integration test) does not need to be declared explicitly — it is already a transitive compile dependency of `grpc-client-spring-boot-starter`.

- [ ] **Step 8: Create the client application class and config**

`communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/GrpcClientDemoApplication.java`:

```java
package com.testingai.grpc.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GrpcClientDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcClientDemoApplication.class, args);
	}
}
```

`communication-protocols/grpc/client-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8091

grpc:
  client:
    catalog-service:
      address: static://localhost:9090
      negotiation-type: plaintext
```

- [ ] **Step 9: Write the client context-load test**

`communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/GrpcClientDemoApplicationTest.java`:

```java
package com.testingai.grpc.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GrpcClientDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

(The gRPC channel to `static://localhost:9090` is created lazily by grpc-java — the context loads successfully even though no server is listening yet.)

- [ ] **Step 10: Build the whole reactor and verify codegen**

```bash
cd communication-protocols
mvn clean package
```

Expected: `BUILD SUCCESS`. Verify generated stub classes exist independently in both modules:

```bash
find grpc/server-demo/target/generated-sources -name "ProductCatalogServiceGrpc.java"
find grpc/client-demo/target/generated-sources -name "ProductCatalogServiceGrpc.java"
```

Expected: one match under each module's own `target/generated-sources/protobuf/grpc-java/com/testingai/grpc/proto/`.

- [ ] **Step 11: Commit**

```bash
git add communication-protocols/
git commit -m "feat(communication-protocols): scaffold gRPC demo reactor and module skeletons"
```

---

### Task 2: `FailureSimulator` (server-demo)

**Files:**
- Create: `communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/util/FailureSimulatorTest.java`
- Create: `communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/util/FailureSimulator.java`

**Interfaces:**
- Produces: `com.testingai.grpc.server.util.FailureSimulator.maybeThrow(String context): void` — used by `ProductCatalogServiceImpl` in Task 4.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.grpc.server.util;

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

```bash
cd communication-protocols
mvn test -pl grpc/server-demo -Dtest=FailureSimulatorTest
```

Expected: FAIL — `FailureSimulator` does not exist / compilation error.

- [ ] **Step 3: Implement `FailureSimulator`**

```java
package com.testingai.grpc.server.util;

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

```bash
mvn test -pl grpc/server-demo -Dtest=FailureSimulatorTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/util/FailureSimulatorTest.java \
        communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/util/FailureSimulator.java
git commit -m "feat(communication-protocols): add FailureSimulator to grpc-server-demo"
```

---

### Task 3: `SampleDataService` (server-demo)

**Files:**
- Create: `communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/domain/SampleDataServiceTest.java`
- Create: `communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/domain/SampleDataService.java`

**Interfaces:**
- Consumes: `com.testingai.grpc.proto.ProductResponse` (generated in Task 1).
- Produces: `com.testingai.grpc.server.domain.SampleDataService.findProduct(String productId): Optional<ProductResponse>`, `.listProducts(): List<ProductResponse>` — used by `ProductCatalogServiceImpl` in Task 4.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.grpc.server.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataServiceTest {

	private final SampleDataService service = new SampleDataService();

	@Test
	void findProduct_returnsProduct_whenIdKnown() {
		assertThat(service.findProduct("p1")).isPresent().get().extracting("name").isEqualTo("Widget");
	}

	@Test
	void findProduct_returnsEmpty_whenIdUnknown() {
		assertThat(service.findProduct("unknown")).isEmpty();
	}

	@Test
	void listProducts_returnsAllFourSampleProducts() {
		assertThat(service.listProducts()).hasSize(4);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl grpc/server-demo -Dtest=SampleDataServiceTest
```

Expected: FAIL — `SampleDataService` does not exist.

- [ ] **Step 3: Implement `SampleDataService`**

```java
package com.testingai.grpc.server.domain;

import com.testingai.grpc.proto.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private final List<ProductResponse> products = List.of(
			ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build(),
			ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build(),
			ProductResponse.newBuilder().setProductId("p3").setName("Gizmo").setPriceCents(2999).build(),
			ProductResponse.newBuilder().setProductId("p4").setName("Doohickey").setPriceCents(499).build());

	public Optional<ProductResponse> findProduct(String productId) {
		return products.stream().filter(product -> product.getProductId().equals(productId)).findFirst();
	}

	public List<ProductResponse> listProducts() {
		return products;
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl grpc/server-demo -Dtest=SampleDataServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/domain/SampleDataServiceTest.java \
        communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/domain/SampleDataService.java
git commit -m "feat(communication-protocols): add SampleDataService to grpc-server-demo"
```

---

### Task 4: `ProductCatalogServiceImpl` — all four RPCs (server-demo)

**Files:**
- Create: `communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/service/ProductCatalogServiceImplTest.java`
- Create: `communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/service/ProductCatalogServiceImpl.java`

**Interfaces:**
- Consumes: `SampleDataService` (Task 3), `FailureSimulator.maybeThrow(String)` (Task 2), generated `com.testingai.grpc.proto.*` classes (Task 1).
- Produces: `com.testingai.grpc.server.service.ProductCatalogServiceImpl` — a `@GrpcService` bean extending `ProductCatalogServiceGrpc.ProductCatalogServiceImplBase`. This is the production RPC implementation that `client-demo` calls over the network (client-demo does not import this class directly — see Task 6 for how it's tested independently).

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.grpc.server.service;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import com.testingai.grpc.server.domain.SampleDataService;
import com.testingai.grpc.server.util.FailureSimulator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceImplTest {

	@Mock
	private StreamObserver<ProductResponse> productObserver;
	@Mock
	private StreamObserver<OrderSummary> orderSummaryObserver;
	@Mock
	private StreamObserver<OrderStatusUpdate> orderStatusObserver;

	private ProductCatalogServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new ProductCatalogServiceImpl(new SampleDataService());
	}

	@Test
	void getProduct_returnsProduct_whenFound() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.getProduct(ProductRequest.newBuilder().setProductId("p1").build(), productObserver);

			ArgumentCaptor<ProductResponse> captor = ArgumentCaptor.forClass(ProductResponse.class);
			verify(productObserver).onNext(captor.capture());
			verify(productObserver).onCompleted();
			assertThat(captor.getValue().getName()).isEqualTo("Widget");
		}
	}

	@Test
	void getProduct_sendsNotFound_whenProductUnknown() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.getProduct(ProductRequest.newBuilder().setProductId("unknown").build(), productObserver);

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.NOT_FOUND);
		}
	}

	@Test
	void getProduct_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			service.getProduct(ProductRequest.newBuilder().setProductId("p1").build(), productObserver);

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void listProducts_streamsAllProducts_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			service.listProducts(ListProductsRequest.getDefaultInstance(), productObserver);

			verify(productObserver, times(4)).onNext(any());
			verify(productObserver).onCompleted();
		}
	}

	@Test
	void listProducts_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			service.listProducts(ListProductsRequest.getDefaultInstance(), productObserver);

			verify(productObserver, never()).onCompleted();
			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(productObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void uploadOrders_returnsSummary_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			StreamObserver<OrderRequest> requestObserver = service.uploadOrders(orderSummaryObserver);
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p1").setQuantity(2).build());
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p2").setQuantity(1).build());
			requestObserver.onCompleted();

			ArgumentCaptor<OrderSummary> captor = ArgumentCaptor.forClass(OrderSummary.class);
			verify(orderSummaryObserver).onNext(captor.capture());
			verify(orderSummaryObserver).onCompleted();
			assertThat(captor.getValue().getOrderCount()).isEqualTo(2);
			assertThat(captor.getValue().getTotalPriceCents()).isEqualTo(999L * 2 + 1999L);
		}
	}

	@Test
	void uploadOrders_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			StreamObserver<OrderRequest> requestObserver = service.uploadOrders(orderSummaryObserver);
			requestObserver.onNext(OrderRequest.newBuilder().setProductId("p1").setQuantity(1).build());
			requestObserver.onCompleted();

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(orderSummaryObserver).onError(captor.capture());
			verify(orderSummaryObserver, never()).onCompleted();
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}

	@Test
	void streamOrderStatus_echoesEachUpdate_whenNoFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(invocation -> null);

			StreamObserver<OrderStatusUpdate> requestObserver = service.streamOrderStatus(orderStatusObserver);
			requestObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("PLACED").build());
			requestObserver.onCompleted();

			ArgumentCaptor<OrderStatusUpdate> captor = ArgumentCaptor.forClass(OrderStatusUpdate.class);
			verify(orderStatusObserver).onNext(captor.capture());
			verify(orderStatusObserver).onCompleted();
			assertThat(captor.getValue().getStatus()).isEqualTo("ACKNOWLEDGED:PLACED");
		}
	}

	@Test
	void streamOrderStatus_sendsInternalError_onSimulatedFailure() {
		try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
			mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenThrow(new RuntimeException("Simulated"));

			StreamObserver<OrderStatusUpdate> requestObserver = service.streamOrderStatus(orderStatusObserver);
			requestObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("PLACED").build());

			ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
			verify(orderStatusObserver).onError(captor.capture());
			assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INTERNAL);
		}
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl grpc/server-demo -Dtest=ProductCatalogServiceImplTest
```

Expected: FAIL — `ProductCatalogServiceImpl` does not exist.

- [ ] **Step 3: Implement `ProductCatalogServiceImpl`**

```java
package com.testingai.grpc.server.service;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import com.testingai.grpc.server.domain.SampleDataService;
import com.testingai.grpc.server.util.FailureSimulator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ProductCatalogServiceImpl extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

	private final SampleDataService sampleDataService;

	public ProductCatalogServiceImpl(SampleDataService sampleDataService) {
		this.sampleDataService = sampleDataService;
	}

	@Override
	public void getProduct(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
		try {
			FailureSimulator.maybeThrow("getProduct");
		} catch (RuntimeException e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
			return;
		}
		sampleDataService.findProduct(request.getProductId()).ifPresentOrElse(product -> {
			responseObserver.onNext(product);
			responseObserver.onCompleted();
		}, () -> responseObserver.onError(Status.NOT_FOUND
				.withDescription("Unknown product: " + request.getProductId()).asRuntimeException()));
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
		for (ProductResponse product : sampleDataService.listProducts()) {
			try {
				FailureSimulator.maybeThrow("listProducts");
			} catch (RuntimeException e) {
				responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
				return;
			}
			responseObserver.onNext(product);
		}
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<OrderRequest> uploadOrders(StreamObserver<OrderSummary> responseObserver) {
		return new StreamObserver<>() {

			private int orderCount = 0;
			private long totalPriceCents = 0;
			private boolean errored = false;

			@Override
			public void onNext(OrderRequest order) {
				if (errored) {
					return;
				}
				try {
					FailureSimulator.maybeThrow("uploadOrders");
				} catch (RuntimeException e) {
					errored = true;
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				sampleDataService.findProduct(order.getProductId()).ifPresent(product -> {
					orderCount++;
					totalPriceCents += product.getPriceCents() * order.getQuantity();
				});
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the upload; nothing to clean up
			}

			@Override
			public void onCompleted() {
				if (errored) {
					return;
				}
				responseObserver.onNext(OrderSummary.newBuilder().setOrderCount(orderCount)
						.setTotalPriceCents(totalPriceCents).build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<OrderStatusUpdate> streamOrderStatus(StreamObserver<OrderStatusUpdate> responseObserver) {
		return new StreamObserver<>() {

			private boolean errored = false;

			@Override
			public void onNext(OrderStatusUpdate update) {
				if (errored) {
					return;
				}
				try {
					FailureSimulator.maybeThrow("streamOrderStatus");
				} catch (RuntimeException e) {
					errored = true;
					responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
					return;
				}
				responseObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId(update.getOrderId())
						.setStatus("ACKNOWLEDGED:" + update.getStatus()).build());
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the stream; nothing to clean up
			}

			@Override
			public void onCompleted() {
				if (!errored) {
					responseObserver.onCompleted();
				}
			}
		};
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl grpc/server-demo -Dtest=ProductCatalogServiceImplTest
```

Expected: PASS (all 8 test cases)

- [ ] **Step 5: Run the full server-demo test suite**

```bash
mvn test -pl grpc/server-demo
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/grpc/server-demo/src/test/java/com/testingai/grpc/server/service/ProductCatalogServiceImplTest.java \
        communication-protocols/grpc/server-demo/src/main/java/com/testingai/grpc/server/service/ProductCatalogServiceImpl.java
git commit -m "feat(communication-protocols): implement ProductCatalogServiceImpl in grpc-server-demo"
```

---

### Task 5: `DemoController` + `DemoExceptionHandler` (client-demo)

**Files:**
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/dto/ProductDto.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/dto/OrderRequestDto.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/dto/OrderSummaryDto.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/dto/OrderStatusUpdateDto.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/config/GrpcStubConfig.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/controller/DemoController.java`
- Create: `communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/controller/DemoExceptionHandler.java`
- Create: `communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: generated `com.testingai.grpc.proto.*` classes (Task 1).
- Produces: REST endpoints under `/demo/grpc/**` on `DemoController` — `GET /demo/grpc/unary/products/{productId}`, `GET /demo/grpc/server-streaming/products`, `POST /demo/grpc/client-streaming/orders`, `POST /demo/grpc/bidi-streaming/order-status`. Used by Task 6 (integration test) and Task 7 (Gatling).

- [ ] **Step 1: Write the failing test**

`communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/controller/DemoControllerTest.java`:

```java
package com.testingai.grpc.client.controller;

import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

	@Mock
	private ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;
	@Mock
	private ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;
	@Mock
	private StreamObserver<OrderRequest> orderRequestObserver;
	@Mock
	private StreamObserver<OrderStatusUpdate> orderStatusRequestObserver;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		DemoController controller = new DemoController(blockingStub, asyncStub);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new DemoExceptionHandler()).build();
	}

	@Test
	void getProduct_returnsProduct() throws Exception {
		when(blockingStub.getProduct(any())).thenReturn(
				ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());

		mockMvc.perform(get("/demo/grpc/unary/products/p1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget")).andExpect(jsonPath("$.priceCents").value(999));
	}

	@Test
	void getProduct_returns404_whenGrpcReportsNotFound() throws Exception {
		when(blockingStub.getProduct(any()))
				.thenThrow(Status.NOT_FOUND.withDescription("Unknown product: p9").asRuntimeException());

		mockMvc.perform(get("/demo/grpc/unary/products/p9")).andExpect(status().isNotFound());
	}

	@Test
	void listProducts_returnsAllStreamedProducts() throws Exception {
		List<ProductResponse> products = List.of(
				ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build(),
				ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build());
		when(blockingStub.listProducts(any())).thenReturn(products.iterator());

		mockMvc.perform(get("/demo/grpc/server-streaming/products")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].name").value("Gadget"));
	}

	@Test
	void uploadOrders_returnsSummary_fromAsyncStub() throws Exception {
		when(asyncStub.uploadOrders(any())).thenAnswer(invocation -> {
			StreamObserver<OrderSummary> responseObserver = invocation.getArgument(0);
			responseObserver.onNext(OrderSummary.newBuilder().setOrderCount(2).setTotalPriceCents(2997).build());
			responseObserver.onCompleted();
			return orderRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderCount").value(2)).andExpect(jsonPath("$.totalPriceCents").value(2997));
	}

	@Test
	void uploadOrders_returns502_whenAsyncStubReportsInternalError() throws Exception {
		when(asyncStub.uploadOrders(any())).thenAnswer(invocation -> {
			StreamObserver<OrderSummary> responseObserver = invocation.getArgument(0);
			responseObserver.onError(Status.INTERNAL.withDescription("Simulated 5% failure").asRuntimeException());
			return orderRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isBadGateway());
	}

	@Test
	void streamOrderStatus_returnsEchoedUpdates() throws Exception {
		when(asyncStub.streamOrderStatus(any())).thenAnswer(invocation -> {
			StreamObserver<OrderStatusUpdate> responseObserver = invocation.getArgument(0);
			responseObserver
					.onNext(OrderStatusUpdate.newBuilder().setOrderId("o1").setStatus("ACKNOWLEDGED:PLACED").build());
			responseObserver.onCompleted();
			return orderStatusRequestObserver;
		});

		mockMvc.perform(post("/demo/grpc/bidi-streaming/order-status").contentType(MediaType.APPLICATION_JSON)
				.content("[{\"orderId\":\"o1\",\"status\":\"PLACED\"}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED:PLACED"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl grpc/client-demo -Dtest=DemoControllerTest
```

Expected: FAIL — `DemoController`/`DemoExceptionHandler` do not exist.

- [ ] **Step 3: Implement the DTOs**

```java
package com.testingai.grpc.client.dto;

public record ProductDto(String productId, String name, long priceCents) {
}
```

```java
package com.testingai.grpc.client.dto;

public record OrderRequestDto(String productId, int quantity) {
}
```

```java
package com.testingai.grpc.client.dto;

public record OrderSummaryDto(int orderCount, long totalPriceCents) {
}
```

```java
package com.testingai.grpc.client.dto;

public record OrderStatusUpdateDto(String orderId, String status) {
}
```

- [ ] **Step 4: Implement `GrpcStubConfig`**

```java
package com.testingai.grpc.client.config;

import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcStubConfig {

	@GrpcClient("catalog-service")
	private ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;

	@GrpcClient("catalog-service")
	private ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;

	@Bean
	public ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub productCatalogBlockingStub() {
		return blockingStub;
	}

	@Bean
	public ProductCatalogServiceGrpc.ProductCatalogServiceStub productCatalogAsyncStub() {
		return asyncStub;
	}
}
```

`@GrpcClient` is net.devh's field-injection annotation — it can't target a constructor parameter directly, so this config class re-exposes each injected stub as a plain `@Bean`, letting `DemoController` (below) receive both stubs through ordinary constructor injection and stay trivially unit-testable with `new DemoController(mockBlockingStub, mockAsyncStub)`.

- [ ] **Step 5: Implement `DemoController`**

```java
package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.dto.OrderRequestDto;
import com.testingai.grpc.client.dto.OrderStatusUpdateDto;
import com.testingai.grpc.client.dto.OrderSummaryDto;
import com.testingai.grpc.client.dto.ProductDto;
import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/demo/grpc")
@RequiredArgsConstructor
public class DemoController {

	private final ProductCatalogServiceGrpc.ProductCatalogServiceBlockingStub blockingStub;
	private final ProductCatalogServiceGrpc.ProductCatalogServiceStub asyncStub;

	@GetMapping("/unary/products/{productId}")
	public ResponseEntity<ProductDto> getProduct(@PathVariable String productId) {
		ProductResponse response = blockingStub.getProduct(ProductRequest.newBuilder().setProductId(productId).build());
		return ResponseEntity.ok(toDto(response));
	}

	@GetMapping("/server-streaming/products")
	public ResponseEntity<List<ProductDto>> listProducts() {
		List<ProductDto> products = new ArrayList<>();
		blockingStub.listProducts(ListProductsRequest.getDefaultInstance())
				.forEachRemaining(product -> products.add(toDto(product)));
		return ResponseEntity.ok(products);
	}

	@PostMapping("/client-streaming/orders")
	public ResponseEntity<OrderSummaryDto> uploadOrders(@RequestBody List<OrderRequestDto> orders) {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<OrderSummary> result = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderRequest> requestObserver = asyncStub.uploadOrders(new StreamObserver<>() {

			@Override
			public void onNext(OrderSummary value) {
				result.set(value);
			}

			@Override
			public void onError(Throwable t) {
				error.set(t);
				latch.countDown();
			}

			@Override
			public void onCompleted() {
				latch.countDown();
			}
		});

		orders.forEach(order -> requestObserver
				.onNext(OrderRequest.newBuilder().setProductId(order.productId()).setQuantity(order.quantity()).build()));
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		return ResponseEntity.ok(toDto(result.get()));
	}

	@PostMapping("/bidi-streaming/order-status")
	public ResponseEntity<List<OrderStatusUpdateDto>> streamOrderStatus(@RequestBody List<OrderStatusUpdateDto> updates) {
		CountDownLatch latch = new CountDownLatch(1);
		List<OrderStatusUpdate> responses = Collections.synchronizedList(new ArrayList<>());
		AtomicReference<Throwable> error = new AtomicReference<>();

		StreamObserver<OrderStatusUpdate> requestObserver = asyncStub.streamOrderStatus(new StreamObserver<>() {

			@Override
			public void onNext(OrderStatusUpdate value) {
				responses.add(value);
			}

			@Override
			public void onError(Throwable t) {
				error.set(t);
				latch.countDown();
			}

			@Override
			public void onCompleted() {
				latch.countDown();
			}
		});

		updates.forEach(update -> requestObserver.onNext(
				OrderStatusUpdate.newBuilder().setOrderId(update.orderId()).setStatus(update.status()).build()));
		requestObserver.onCompleted();

		awaitLatch(latch);
		if (error.get() != null) {
			throw (RuntimeException) error.get();
		}
		return ResponseEntity.ok(responses.stream().map(this::toDto).toList());
	}

	private void awaitLatch(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for gRPC response", e);
		}
	}

	private ProductDto toDto(ProductResponse response) {
		return new ProductDto(response.getProductId(), response.getName(), response.getPriceCents());
	}

	private OrderSummaryDto toDto(OrderSummary summary) {
		return new OrderSummaryDto(summary.getOrderCount(), summary.getTotalPriceCents());
	}

	private OrderStatusUpdateDto toDto(OrderStatusUpdate update) {
		return new OrderStatusUpdateDto(update.getOrderId(), update.getStatus());
	}
}
```

- [ ] **Step 6: Implement `DemoExceptionHandler`**

```java
package com.testingai.grpc.client.controller;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(StatusRuntimeException.class)
	public ResponseEntity<String> handleGrpcError(StatusRuntimeException exception) {
		HttpStatus httpStatus = switch (exception.getStatus().getCode()) {
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case INTERNAL -> HttpStatus.BAD_GATEWAY;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
		return ResponseEntity.status(httpStatus)
				.body(exception.getStatus().getCode() + ": " + exception.getStatus().getDescription());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
	}
}
```

- [ ] **Step 7: Run test to verify it passes**

```bash
mvn test -pl grpc/client-demo -Dtest=DemoControllerTest
```

Expected: PASS (all 6 test cases)

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/dto/ \
        communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/config/GrpcStubConfig.java \
        communication-protocols/grpc/client-demo/src/main/java/com/testingai/grpc/client/controller/ \
        communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/controller/DemoControllerTest.java
git commit -m "feat(communication-protocols): implement DemoController in grpc-client-demo"
```

---

### Task 6: End-to-end integration test (client-demo)

**Files:**
- Create: `communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/support/FakeProductCatalogService.java`
- Create: `communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/controller/DemoIntegrationTest.java`

**Interfaces:**
- Consumes: `DemoController`'s REST endpoints (Task 5); generated `ProductCatalogServiceGrpc.ProductCatalogServiceImplBase` (regenerated locally in `client-demo`, Task 1).
- No new production code — this task only adds tests plus a test-only fake gRPC service. `FakeProductCatalogService` is independent from `server-demo`'s `ProductCatalogServiceImpl` (there is deliberately no Maven dependency between the two modules — see File Structure notes); it exists purely to exercise real, in-process gRPC serialization and dispatch through `DemoController` without needing a second running process.

- [ ] **Step 1: Write `FakeProductCatalogService`**

```java
package com.testingai.grpc.client.support;

import com.testingai.grpc.proto.ListProductsRequest;
import com.testingai.grpc.proto.OrderRequest;
import com.testingai.grpc.proto.OrderStatusUpdate;
import com.testingai.grpc.proto.OrderSummary;
import com.testingai.grpc.proto.ProductCatalogServiceGrpc;
import com.testingai.grpc.proto.ProductRequest;
import com.testingai.grpc.proto.ProductResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class FakeProductCatalogService extends ProductCatalogServiceGrpc.ProductCatalogServiceImplBase {

	@Override
	public void getProduct(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
		switch (request.getProductId()) {
			case "p1" -> {
				responseObserver
						.onNext(ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());
				responseObserver.onCompleted();
			}
			case "fail-trigger" ->
				responseObserver.onError(Status.INTERNAL.withDescription("Simulated failure").asRuntimeException());
			default -> responseObserver.onError(
					Status.NOT_FOUND.withDescription("Unknown product: " + request.getProductId()).asRuntimeException());
		}
	}

	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ProductResponse> responseObserver) {
		responseObserver
				.onNext(ProductResponse.newBuilder().setProductId("p1").setName("Widget").setPriceCents(999).build());
		responseObserver
				.onNext(ProductResponse.newBuilder().setProductId("p2").setName("Gadget").setPriceCents(1999).build());
		responseObserver.onCompleted();
	}

	@Override
	public StreamObserver<OrderRequest> uploadOrders(StreamObserver<OrderSummary> responseObserver) {
		return new StreamObserver<>() {

			private int count = 0;

			@Override
			public void onNext(OrderRequest value) {
				count++;
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the upload; nothing to clean up
			}

			@Override
			public void onCompleted() {
				responseObserver
						.onNext(OrderSummary.newBuilder().setOrderCount(count).setTotalPriceCents(count * 999L).build());
				responseObserver.onCompleted();
			}
		};
	}

	@Override
	public StreamObserver<OrderStatusUpdate> streamOrderStatus(StreamObserver<OrderStatusUpdate> responseObserver) {
		return new StreamObserver<>() {

			@Override
			public void onNext(OrderStatusUpdate value) {
				responseObserver.onNext(OrderStatusUpdate.newBuilder().setOrderId(value.getOrderId())
						.setStatus("ACKNOWLEDGED:" + value.getStatus()).build());
			}

			@Override
			public void onError(Throwable t) {
				// client cancelled the stream; nothing to clean up
			}

			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}
		};
	}
}
```

- [ ] **Step 2: Write `DemoIntegrationTest`**

```java
package com.testingai.grpc.client.controller;

import com.testingai.grpc.client.support.FakeProductCatalogService;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "grpc.client.catalog-service.address=in-process:demo-integration-test")
@AutoConfigureMockMvc
class DemoIntegrationTest {

	private static Server inProcessServer;

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	static void startFakeServer() throws IOException {
		inProcessServer = InProcessServerBuilder.forName("demo-integration-test").directExecutor()
				.addService(new FakeProductCatalogService()).build().start();
	}

	@AfterAll
	static void stopFakeServer() {
		inProcessServer.shutdownNow();
	}

	@Test
	void unary_returnsProduct_endToEnd() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/p1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Widget"));
	}

	@Test
	void unary_returns404_whenProductUnknown() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/unknown")).andExpect(status().isNotFound());
	}

	@Test
	void unary_returns502_onSimulatedServerError() throws Exception {
		mockMvc.perform(get("/demo/grpc/unary/products/fail-trigger")).andExpect(status().isBadGateway());
	}

	@Test
	void serverStreaming_returnsAllProducts_endToEnd() throws Exception {
		mockMvc.perform(get("/demo/grpc/server-streaming/products")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void clientStreaming_returnsSummary_endToEnd() throws Exception {
		mockMvc.perform(post("/demo/grpc/client-streaming/orders").contentType("application/json")
				.content("[{\"productId\":\"p1\",\"quantity\":2}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderCount").value(1));
	}

	@Test
	void bidiStreaming_echoesEachUpdate_endToEnd() throws Exception {
		mockMvc.perform(post("/demo/grpc/bidi-streaming/order-status").contentType("application/json")
				.content("[{\"orderId\":\"o1\",\"status\":\"PLACED\"}]")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED:PLACED"));
	}
}
```

- [ ] **Step 3: Run the test**

```bash
mvn test -pl grpc/client-demo -Dtest=DemoIntegrationTest
```

Expected: PASS (all 6 test cases). This proves `GrpcStubConfig`'s `@GrpcClient` beans, `DemoController`'s async latch-based collection, and `DemoExceptionHandler`'s status mapping all work against a genuine (in-process) gRPC round trip, not hand-mocked stubs.

- [ ] **Step 4: Run the full client-demo test suite**

```bash
mvn test -pl grpc/client-demo
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/support/FakeProductCatalogService.java \
        communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/controller/DemoIntegrationTest.java
git commit -m "test(communication-protocols): add end-to-end gRPC integration test to grpc-client-demo"
```

---

### Task 7: Gatling load test (client-demo)

**Files:**
- Create: `communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: `DemoController`'s REST endpoints (Task 5) — exercised as a running HTTP server, not called in-process.

- [ ] **Step 1: Write the simulation**

```java
package com.testingai.grpc.client.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private static final String CLIENT_STREAMING_BODY = """
			[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}]""";

	private static final String BIDI_STREAMING_BODY = """
			[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"SHIPPED"}]""";

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8091")
			.contentTypeHeader("application/json");

	private final ScenarioBuilder demoScenario = scenario("gRPC Demo")
			.exec(http("Unary - Get Product").get("/demo/grpc/unary/products/p1").check(status().is(200)))
			.exec(http("Server Streaming - List Products").get("/demo/grpc/server-streaming/products")
					.check(status().is(200)))
			.exec(http("Client Streaming - Upload Orders").post("/demo/grpc/client-streaming/orders")
					.body(StringBody(CLIENT_STREAMING_BODY)).check(status().is(200)))
			.exec(http("Bidi Streaming - Order Status").post("/demo/grpc/bidi-streaming/order-status")
					.body(StringBody(BIDI_STREAMING_BODY)).check(status().is(200)));

	{
		setUp(demoScenario.injectOpen(atOnceUsers(10))).protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it's excluded from `mvn test` and compiles**

```bash
mvn test -pl grpc/client-demo
```

Expected: PASS, and the test output does not mention `DemoSimulation` (excluded by the inherited surefire `**/performance/**` pattern).

```bash
mvn test-compile -pl grpc/client-demo
```

Expected: `BUILD SUCCESS` (proves `DemoSimulation` itself compiles even though surefire skips running it).

- [ ] **Step 3: Commit**

```bash
git add communication-protocols/grpc/client-demo/src/test/java/com/testingai/grpc/client/performance/DemoSimulation.java
git commit -m "test(communication-protocols): add Gatling load test to grpc-client-demo"
```

---

### Task 8: READMEs, `CLAUDE.md`, and pre-commit hook wiring

**Files:**
- Create: `communication-protocols/README.md`
- Create: `communication-protocols/grpc/README.md`
- Create: `communication-protocols/grpc/server-demo/README.md`
- Create: `communication-protocols/grpc/client-demo/README.md`
- Modify: `CLAUDE.md`
- Modify: `.githooks/pre-commit`

**Interfaces:** None — this task only adds documentation and wires the new category into repo-wide tooling that every other category already goes through.

- [ ] **Step 1: Write the category README**

`communication-protocols/README.md`:

```markdown
# Communication Protocols — Demos

This directory contains runnable demos for communication protocols used between services, structured the same way as `../distributed-transactions/`: one protocol per subdirectory, no external infrastructure required.

| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |

More protocol demos may be added here over time (e.g. GraphQL, WebSocket).
```

- [ ] **Step 2: Write the gRPC module README**

`communication-protocols/grpc/README.md`:

```markdown
# gRPC Demo

Demonstrates gRPC — a high-performance RPC framework built on HTTP/2 and Protocol Buffers — via two independent Spring Boot apps that talk to each other over real gRPC:

- **[server-demo](server-demo/)** — owns the `.proto` contract (`ProductCatalogService`) and implements all four RPCs against an in-memory product catalog.
- **[client-demo](client-demo/)** — exposes a REST facade (`DemoController`) that drives each RPC pattern via a genuine gRPC call, so the whole demo can be exercised with `curl`.

## The four RPC patterns

| Pattern | RPC | What it demonstrates |
|---|---|---|
| Unary | `GetProduct` | Simple request/response — the gRPC equivalent of a single REST call |
| Server streaming | `ListProducts` | Server pushes a sequence of responses over one call |
| Client streaming | `UploadOrders` | Client pushes a sequence of requests, server replies once at the end |
| Bidirectional streaming | `StreamOrderStatus` | Client and server exchange messages independently over the same call |

## Running the demo

`server-demo` must be started first — `client-demo` connects to it at `localhost:9090` on startup.

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run   # terminal 1
mvn -pl grpc/client-demo spring-boot:run   # terminal 2
```

See [client-demo/README.md](client-demo/README.md) for `curl` walkthroughs of all four patterns.

## Scope

Plaintext gRPC only — no TLS, no retries/deadlines beyond gRPC's defaults, no persistence. This is a protocol-pattern demo, not a production hardening guide. `server-demo` and `client-demo` share the `.proto` contract at build time (`client-demo` points its own codegen at `server-demo/src/main/proto`) but have no Maven dependency on each other — they are independently buildable and deployable.
```

- [ ] **Step 3: Write the server-demo README**

`communication-protocols/grpc/server-demo/README.md`:

```markdown
# gRPC Server Demo

Implements `ProductCatalogService` — the gRPC service used by [client-demo](../client-demo/) to demonstrate all four RPC patterns.

## Prerequisites

Java 21, Maven. No Docker — the product catalog is in-memory.

## Run

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run
```

The gRPC server listens on `localhost:9090` (plaintext). An HTTP port (`9091`) is exposed only for `/actuator/health`.

## Patterns implemented

| RPC | Pattern |
|---|---|
| `GetProduct` | Unary |
| `ListProducts` | Server streaming |
| `UploadOrders` | Client streaming |
| `StreamOrderStatus` | Bidirectional streaming |

Every RPC calls `FailureSimulator.maybeThrow(...)` first (5% chance), which is caught and turned into a `Status.INTERNAL` gRPC error — see [client-demo/README.md](../client-demo/README.md) for how that surfaces over REST.

## Build & test

```bash
mvn clean package                    # build (also regenerates gRPC stubs from src/main/proto/catalog.proto)
mvn test                             # unit tests
mvn test -Dtest=ClassName            # single test class
```
```

- [ ] **Step 4: Write the client-demo README**

`communication-protocols/grpc/client-demo/README.md`:

```markdown
# gRPC Client Demo

REST facade over `ProductCatalogService`. Each endpoint below makes one genuine gRPC call to [server-demo](../server-demo/) (which must already be running on `localhost:9090`) and translates the result to/from JSON.

## Prerequisites

Java 21, Maven. No Docker.

## Run

```bash
cd communication-protocols
mvn -pl grpc/server-demo spring-boot:run   # terminal 1 — must be running first
mvn -pl grpc/client-demo spring-boot:run   # terminal 2
```

Swagger UI: http://localhost:8091/swagger-ui/index.html

## Walkthrough

**Unary — get one product:**

```bash
curl http://localhost:8091/demo/grpc/unary/products/p1
# {"productId":"p1","name":"Widget","priceCents":999}

curl -i http://localhost:8091/demo/grpc/unary/products/unknown
# HTTP/1.1 404 ...
```

**Server streaming — list the whole catalog:**

```bash
curl http://localhost:8091/demo/grpc/server-streaming/products
# [{"productId":"p1","name":"Widget","priceCents":999}, ...]
```

**Client streaming — upload a batch of orders, get one summary back:**

```bash
curl -X POST http://localhost:8091/demo/grpc/client-streaming/orders \
  -H 'Content-Type: application/json' \
  -d '[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}]'
# {"orderCount":2,"totalPriceCents":2997}
```

**Bidirectional streaming — send status updates, get each one echoed back acknowledged:**

```bash
curl -X POST http://localhost:8091/demo/grpc/bidi-streaming/order-status \
  -H 'Content-Type: application/json' \
  -d '[{"orderId":"o1","status":"PLACED"},{"orderId":"o1","status":"SHIPPED"}]'
# [{"orderId":"o1","status":"ACKNOWLEDGED:PLACED"},{"orderId":"o1","status":"ACKNOWLEDGED:SHIPPED"}]
```

**Simulated failure:** every RPC has a 5% chance of failing server-side (`FailureSimulator`). When it does, any of the above returns `502 Bad Gateway` with the gRPC status code and description in the body — repeat a call a few times to see it.

## Build & test

```bash
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires both apps running first
```
```

- [ ] **Step 5: Add a command section to `CLAUDE.md`**

In `CLAUDE.md`, insert this new section immediately after the "Saga pattern demo" section (after its closing ` ``` ` and blank line) and before the "Spring Boot starter demo" section:

```markdown
### gRPC communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                            # build both apps (reactor build)
mvn test                                                      # unit tests for both modules (Gatling excluded automatically)
mvn test -pl grpc/client-demo -Dtest=ClassName                 # single test class
mvn -pl grpc/server-demo spring-boot:run                       # run the server first (gRPC :9090)
mvn -pl grpc/client-demo spring-boot:run                       # then the client (REST :8091)
mvn gatling:test -pl grpc/client-demo                           # load test — requires both apps running first
```
```

- [ ] **Step 6: Add a repository layout row to `CLAUDE.md`**

In the "Repository layout" table, add this row immediately after the `distributed-transactions/<pattern>/spring-demo/` row:

```markdown
| `communication-protocols/grpc/{server-demo,client-demo}/` | gRPC demo — two independent Spring Boot apps covering all four RPC patterns (unary, server/client/bidi streaming); `server-demo` must be started before `client-demo` — no external infrastructure required |
```

- [ ] **Step 7: Extend the pre-commit hook**

In `.githooks/pre-commit`, change:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters)/.*\.java$' || true)
```

to:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols)/.*\.java$' || true)
```

and add this block after the `spring-boot-starters` block (before the "Re-stage the originally staged files" comment):

```bash
if echo "$STAGED_JAVA" | grep -q '^communication-protocols/'; then
    echo "[pre-commit] Applying Spotless formatting to staged communication-protocols Java files..."
    (cd "$ROOT/communication-protocols" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 8: Verify the full reactor still builds**

```bash
cd communication-protocols
mvn clean package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/README.md communication-protocols/grpc/README.md \
        communication-protocols/grpc/server-demo/README.md communication-protocols/grpc/client-demo/README.md \
        CLAUDE.md .githooks/pre-commit
git commit -m "docs(communication-protocols): add module/category READMEs, wire CLAUDE.md and pre-commit hook"
```
