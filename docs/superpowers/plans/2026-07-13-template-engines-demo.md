# Template Engines Demo (Handlebars & FreeMarker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `template-engines/` Maven reactor with two independent Spring Boot demo apps — `handlebars-demo` and `freemarker-demo` — each combining a real Spring MVC view layer with a REST `DemoController` exposing one endpoint per framework capability.

**Architecture:** Both modules render the same in-memory `Product`/`Order` domain (own copies per module, no shared library). Each module has a `PageController` using the engine's real Spring MVC `ViewResolver` (real `.hbs`/`.ftlh` templates, browsable pages) and a `DemoController` with REST endpoints, one per capability, rendering ad hoc templates and returning the fragment in the response body — the "trigger endpoint per pattern" convention already used throughout this repo.

**Tech Stack:** Spring Boot 3.4.4, Java 21, `com.github.jknack:handlebars`/`handlebars-springmvc` 4.5.2 (4.3.1 was tried first but its `HandlebarsView` still referenced `javax.servlet`, incompatible with Spring Boot 3's Jakarta EE — 4.5.2, released Aug 2025, fixed this), `spring-boot-starter-freemarker` (FreeMarker 2.3.3x), Lombok, springdoc-openapi, JUnit 5 + AssertJ + Mockito, Gatling.

## Global Constraints

- Java 21, Spring Boot 3.4.4 (matches every other module in this repo).
- No `util/FailureSimulator` in either module — nothing external to fail against (per the approved design spec).
- Prefer records, pattern matching, switch expressions, text blocks, `SequencedCollection` (`getFirst()`/`getLast()`) over pre-Java-21 idioms, on any line this plan adds.
- No explicit `.toString()` on values passed to SLF4J `{}` placeholders or string concatenation, **except** where the surrounding API requires a compile-time `String`/`CharSequence` return type (e.g. a Handlebars `Helper<T>.apply(...)` must return `CharSequence`) — that exception is called out inline in `.claude/rules/code-review.md`.
- All instance fields assigned once must be `private final`, except fields assigned in `@PostConstruct`/`ApplicationRunner.run()`/`@PreDestroy`, which must still be `private`.
- Formatting is enforced by Spotless (`spotless:apply`, wired into `template-engines/pom.xml`'s git hook) — do not hand-format; let Spotless reformat on commit.
- No docker-compose / external infrastructure in either module — both engines are pure in-process rendering libraries.
- FreeMarker template files use the `.ftlh` extension (Spring Boot 3's default, enables HTML auto-escaping) — not `.ftl`.
- Handlebars template files use the `.hbs` extension.

---

### Task 1: Scaffold the `template-engines/` Maven reactor

**Files:**
- Create: `template-engines/pom.xml`
- Create: `template-engines/eclipse-formatter.xml`
- Create: `template-engines/README.md`

**Interfaces:**
- Produces: Maven parent coordinates `com.testingai:template-engines:1.0.0` (packaging `pom`), properties `handlebars.version`, `lombok.version`, `springdoc.version`, `gatling.version`, `gatling-maven-plugin.version`, `spotless.version` — consumed by both module POMs (Tasks 2 and 9).

- [ ] **Step 1: Create the parent POM**

`template-engines/pom.xml`:

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
    <artifactId>template-engines</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Template Engines</name>
    <description>Parent POM for all template-engine demo modules</description>

    <modules>
        <module>handlebars/spring-demo</module>
        <module>freemarker/spring-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <springdoc.version>2.8.6</springdoc.version>
        <handlebars.version>4.5.2</handlebars.version>
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

`template-engines/eclipse-formatter.xml` — identical content to `noSQL/eclipse-formatter.xml`:

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

- [ ] **Step 3: Write a placeholder top-level README (finalized in Task 16)**

`template-engines/README.md`:

```markdown
# Template Engines — Demos

This directory contains runnable demos for Java template-rendering libraries, structured the same way as `../noSQL/`: one Spring Boot demo app per technology, no external infrastructure required.

| Engine | Demo | Best fit |
|---|---|---|
| [Handlebars](handlebars/) | `com.github.jknack:handlebars` | Logic-less, Mustache-compatible templates; helpers/partials as the only escape hatch |
| [FreeMarker](freemarker/) | `spring-boot-starter-freemarker` | Full-featured templating language: macros, functions, directives, built-ins |

More template engines may be added here over time, at which point this README will grow into a comparison guide like `../message-brokers/README.md`.
```

- [ ] **Step 4: Commit**

```bash
git add template-engines/pom.xml template-engines/eclipse-formatter.xml template-engines/README.md
git commit -m "feat(template-engines): scaffold template-engines Maven reactor"
```

---

### Task 2: Scaffold the `handlebars-demo` module skeleton

**Files:**
- Create: `template-engines/handlebars/spring-demo/pom.xml`
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/HandlebarsDemoApplication.java`
- Create: `template-engines/handlebars/spring-demo/src/main/resources/application.yml`
- Test: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/HandlebarsDemoApplicationTest.java`

**Interfaces:**
- Produces: `com.testingai.handlebars.HandlebarsDemoApplication` (Spring Boot main class), Maven coordinates `com.testingai:handlebars-demo`, server port `8085`.

- [ ] **Step 1: Create the module POM**

`template-engines/handlebars/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>template-engines</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>handlebars-demo</artifactId>
    <name>Handlebars Demo</name>
    <description>Learning and demonstration project for Handlebars.java templating capabilities</description>

    <dependencies>
        <dependency>
            <groupId>com.github.jknack</groupId>
            <artifactId>handlebars</artifactId>
            <version>${handlebars.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.jknack</groupId>
            <artifactId>handlebars-springmvc</artifactId>
            <version>${handlebars.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.handlebars.HandlebarsDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.handlebars.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the Spring Boot main class**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/HandlebarsDemoApplication.java`:

```java
package com.testingai.handlebars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HandlebarsDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(HandlebarsDemoApplication.class, args);
	}
}
```

- [ ] **Step 3: Create `application.yml`**

`template-engines/handlebars/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8085
```

- [ ] **Step 4: Write the application smoke test**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/HandlebarsDemoApplicationTest.java`:

```java
package com.testingai.handlebars;

import org.junit.jupiter.api.Test;

class HandlebarsDemoApplicationTest {

	@Test
	void mainClassExists() {
		new HandlebarsDemoApplication();
	}
}
```

- [ ] **Step 5: Build the reactor**

Run: `cd template-engines && mvn clean package`
Expected: `BUILD SUCCESS`, with `HandlebarsDemoApplicationTest` reported passing.

- [ ] **Step 6: Commit**

```bash
git add template-engines/handlebars/spring-demo/pom.xml \
  template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/HandlebarsDemoApplication.java \
  template-engines/handlebars/spring-demo/src/main/resources/application.yml \
  template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/HandlebarsDemoApplicationTest.java
git commit -m "feat(handlebars): scaffold handlebars-demo module"
```

---

### Task 3: Handlebars domain model and sample data

**Files:**
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/Product.java`
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/OrderItem.java`
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/Order.java`
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/service/SampleDataService.java`
- Test: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/service/SampleDataServiceTest.java`

**Interfaces:**
- Produces: `Product(String id, String name, BigDecimal price, int stock)`, `OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal)`, `Order(String id, String customer, List<OrderItem> items, BigDecimal total, String status, Instant placedAt)`, and `SampleDataService` with `findAllProducts(): List<Product>`, `findOrder(String id): Optional<Order>`, `findAllOrders(): List<Order>` — consumed by `PageController` (Task 5) and `DemoController` (Task 6). Order `"o2"` deliberately has a `null` `status` field, used later by capability demos that need a sparse/missing value.

- [ ] **Step 1: Create the domain records**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/Product.java`:

```java
package com.testingai.handlebars.model;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, int stock) {
}
```

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/OrderItem.java`:

```java
package com.testingai.handlebars.model;

import java.math.BigDecimal;

public record OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal) {
}
```

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/Order.java`:

```java
package com.testingai.handlebars.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(String id, String customer, List<OrderItem> items, BigDecimal total, String status,
		Instant placedAt) {
}
```

- [ ] **Step 2: Write the failing test**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/service/SampleDataServiceTest.java`:

```java
package com.testingai.handlebars.service;

import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataServiceTest {

	private final SampleDataService service = new SampleDataService();

	@Test
	void findAllProducts_shouldReturnFourSeededProducts() {
		List<Product> products = service.findAllProducts();

		assertThat(products).hasSize(4);
		assertThat(products).extracting(Product::id).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	void findOrder_shouldReturnOrderWhenPresent() {
		Optional<Order> order = service.findOrder("o1");

		assertThat(order).isPresent();
		assertThat(order.get().customer()).isEqualTo("Alice");
	}

	@Test
	void findOrder_shouldReturnEmptyWhenMissing() {
		assertThat(service.findOrder("missing")).isEmpty();
	}

	@Test
	void findAllOrders_shouldReturnTwoSeededOrders() {
		assertThat(service.findAllOrders()).hasSize(2);
	}

	@Test
	void secondSeededOrder_shouldHaveNullStatusForNullSafetyDemos() {
		Order order = service.findOrder("o2").orElseThrow();

		assertThat(order.status()).isNull();
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd template-engines/handlebars/spring-demo && mvn test -Dtest=SampleDataServiceTest`
Expected: COMPILATION FAILURE — `SampleDataService` does not exist yet.

- [ ] **Step 4: Implement `SampleDataService`**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/service/SampleDataService.java`:

```java
package com.testingai.handlebars.service;

import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.OrderItem;
import com.testingai.handlebars.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private final List<Product> products = List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100),
			new Product("p2", "Gadget", new BigDecimal("19.99"), 50),
			new Product("p3", "Gizmo", new BigDecimal("29.99"), 0),
			new Product("p4", "Doohickey", new BigDecimal("4.99"), 200));

	private final List<Order> orders = List.of(
			new Order("o1", "Alice",
					List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98")),
							new OrderItem("p2", "Gadget", 1, new BigDecimal("19.99"))),
					new BigDecimal("39.97"), "CONFIRMED", Instant.parse("2026-07-01T10:15:30Z")),
			new Order("o2", "Bob", List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))),
					new BigDecimal("14.97"), null, Instant.parse("2026-07-05T08:00:00Z")));

	public List<Product> findAllProducts() {
		return products;
	}

	public Optional<Order> findOrder(String id) {
		return orders.stream().filter(order -> order.id().equals(id)).findFirst();
	}

	public List<Order> findAllOrders() {
		return orders;
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SampleDataServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/model/ \
  template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/service/ \
  template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/service/
git commit -m "feat(handlebars): add Product/Order domain model and sample data"
```

---

### Task 4: `HandlebarsConfig` — engine wiring and custom helpers

**Files:**
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/config/HandlebarsConfig.java`
- Test: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/config/HandlebarsConfigTest.java`

**Interfaces:**
- Produces: `HandlebarsConfig.TEMPLATE_PREFIX = "/templates"`, `TEMPLATE_SUFFIX = ".hbs"`, `FORMAT_CURRENCY_HELPER = "formatCurrency"`, `MULTIPLY_HELPER = "multiply"`; Spring beans `Handlebars handlebars()` (backed by a `ClassPathTemplateLoader("/templates", ".hbs")`, both helpers registered) and `HandlebarsViewResolver handlebarsViewResolver()` (same prefix/suffix and helpers, for MVC page rendering). Consumed by `DemoController` (Task 6, injects the `Handlebars` bean) and implicitly by Spring MVC (the `HandlebarsViewResolver` bean, Task 5).
- The `formatCurrency` helper accepts `Object` (not just `BigDecimal`) because it must also handle the `String` result of a nested subexpression like `{{formatCurrency (multiply price qty)}}` — Handlebars helpers always return `CharSequence`, so `multiply`'s result arrives at `formatCurrency` as a `String`, not a `BigDecimal`.

- [ ] **Step 1: Write the failing test**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/config/HandlebarsConfigTest.java`:

```java
package com.testingai.handlebars.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HandlebarsConfigTest {

	private final Handlebars handlebars = new HandlebarsConfig().handlebars();

	@Test
	void formatCurrencyHelper_shouldFormatBigDecimalAsTwoDecimalDollarAmount() throws IOException {
		Template template = handlebars.compileInline("{{formatCurrency price}}");

		String result = template.apply(new PriceHolder(new BigDecimal("9.5")));

		assertThat(result).isEqualTo("$9.50");
	}

	@Test
	void formatCurrencyHelper_shouldAcceptStringInputFromASubexpression() throws IOException {
		Template template = handlebars.compileInline("{{formatCurrency \"12.3\"}}");

		String result = template.apply(null);

		assertThat(result).isEqualTo("$12.30");
	}

	@Test
	void multiplyHelper_shouldMultiplyValueByParam() throws IOException {
		Template template = handlebars.compileInline("{{multiply price 3}}");

		String result = template.apply(new PriceHolder(new BigDecimal("2.00")));

		assertThat(new BigDecimal(result)).isEqualByComparingTo(new BigDecimal("6.00"));
	}

	public record PriceHolder(BigDecimal price) {
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=HandlebarsConfigTest`
Expected: COMPILATION FAILURE — `HandlebarsConfig` does not exist yet.

- [ ] **Step 3: Implement `HandlebarsConfig`**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/config/HandlebarsConfig.java`:

```java
package com.testingai.handlebars.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.springmvc.HandlebarsViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class HandlebarsConfig {

	public static final String TEMPLATE_PREFIX = "/templates";
	public static final String TEMPLATE_SUFFIX = ".hbs";
	public static final String FORMAT_CURRENCY_HELPER = "formatCurrency";
	public static final String MULTIPLY_HELPER = "multiply";

	@Bean
	public Handlebars handlebars() {
		Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader(TEMPLATE_PREFIX, TEMPLATE_SUFFIX));
		handlebars.registerHelper(FORMAT_CURRENCY_HELPER, formatCurrencyHelper());
		handlebars.registerHelper(MULTIPLY_HELPER, multiplyHelper());
		return handlebars;
	}

	@Bean
	public HandlebarsViewResolver handlebarsViewResolver() {
		HandlebarsViewResolver resolver = new HandlebarsViewResolver();
		resolver.setPrefix("classpath:" + TEMPLATE_PREFIX + "/");
		resolver.setSuffix(TEMPLATE_SUFFIX);
		resolver.registerHelper(FORMAT_CURRENCY_HELPER, formatCurrencyHelper());
		resolver.registerHelper(MULTIPLY_HELPER, multiplyHelper());
		return resolver;
	}

	private Helper<Object> formatCurrencyHelper() {
		return (value, options) -> {
			if (value == null) {
				return "$0.00";
			}
			BigDecimal amount = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
			return String.format("$%.2f", amount);
		};
	}

	private Helper<Number> multiplyHelper() {
		return (value, options) -> {
			Number factor = options.param(0);
			return toBigDecimal(value).multiply(toBigDecimal(factor)).toString();
		};
	}

	private static BigDecimal toBigDecimal(Number number) {
		return number instanceof BigDecimal bd ? bd : BigDecimal.valueOf(number.doubleValue());
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=HandlebarsConfigTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/config/ \
  template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/config/
git commit -m "feat(handlebars): add HandlebarsConfig with formatCurrency/multiply helpers"
```

---

### Task 5: Handlebars templates and `PageController`

**Files:**
- Create: `template-engines/handlebars/spring-demo/src/main/resources/templates/layout.hbs`
- Create: `template-engines/handlebars/spring-demo/src/main/resources/templates/products.hbs`
- Create: `template-engines/handlebars/spring-demo/src/main/resources/templates/order-detail.hbs`
- Create: `template-engines/handlebars/spring-demo/src/main/resources/templates/partials/order-item.hbs`
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/PageController.java`
- Test: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/PageControllerTest.java`

**Interfaces:**
- Consumes: `SampleDataService` (Task 3), `HandlebarsConfig` (Task 4).
- Produces: `GET /pages/products` and `GET /pages/orders/{id}` (404 via `ResponseStatusException` when the order is missing) — real Spring MVC view resolution, browsable in a browser.

- [ ] **Step 1: Create the layout template**

`template-engines/handlebars/spring-demo/src/main/resources/templates/layout.hbs`:

```handlebars
<!DOCTYPE html>
<html>
<head><title>{{#block "title"}}Template Engines Demo{{/block}}</title></head>
<body>
<header><h1>Handlebars Demo</h1></header>
<main>{{#block "body"}}<p>Default body content.</p>{{/block}}</main>
</body>
</html>
```

- [ ] **Step 2: Create the order-item partial**

`template-engines/handlebars/spring-demo/src/main/resources/templates/partials/order-item.hbs`:

```handlebars
<div class="order-item">
  <span>{{productName}}</span> x {{quantity}} = {{formatCurrency lineTotal}}
</div>
```

- [ ] **Step 3: Create the products page**

`template-engines/handlebars/spring-demo/src/main/resources/templates/products.hbs`:

```handlebars
{{#partial "title"}}Products{{/partial}}
{{#partial "body"}}
<table>
  <tr><th>Name</th><th>Price</th><th>Stock</th></tr>
  {{#each products}}
  <tr><td>{{name}}</td><td>{{formatCurrency price}}</td><td>{{stock}}</td></tr>
  {{/each}}
</table>
{{/partial}}
{{> layout}}
```

- [ ] **Step 4: Create the order-detail page**

`template-engines/handlebars/spring-demo/src/main/resources/templates/order-detail.hbs`:

```handlebars
{{#partial "title"}}Order {{order.id}}{{/partial}}
{{#partial "body"}}
{{#with order}}
<h2>Order {{id}} for {{customer}}</h2>
{{#if items}}
<ul>
  {{#each items}}
  <li>{{> partials/order-item}}</li>
  {{/each}}
</ul>
{{else}}
<p>No items.</p>
{{/if}}
{{#unless status}}<p>Status: pending</p>{{/unless}}
<p>Total: {{formatCurrency total}}</p>
{{/with}}
{{/partial}}
{{> layout}}
```

- [ ] **Step 5: Write the failing test**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/PageControllerTest.java`:

```java
package com.testingai.handlebars.controller;

import com.testingai.handlebars.config.HandlebarsConfig;
import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.OrderItem;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
@Import(HandlebarsConfig.class)
class PageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void products_shouldRenderProductTableWithinLayout() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/pages/products")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("$9.99")))
				.andExpect(content().string(containsString("Handlebars Demo")));
	}

	@Test
	void orderDetail_shouldRenderOrderItemsWithinLayout() throws Exception {
		Order order = new Order("o1", "Alice",
				List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98"))), new BigDecimal("19.98"),
				"CONFIRMED", Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o1")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString("Widget")));
	}

	@Test
	void orderDetail_shouldShowPendingStatusWhenNull() throws Exception {
		Order order = new Order("o2", "Bob",
				List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))), new BigDecimal("14.97"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findOrder("o2")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o2")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Status: pending")));
	}

	@Test
	void orderDetail_shouldReturn404WhenOrderMissing() throws Exception {
		when(sampleDataService.findOrder("missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/pages/orders/missing")).andExpect(status().isNotFound());
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `mvn test -Dtest=PageControllerTest`
Expected: COMPILATION FAILURE — `PageController` does not exist yet.

- [ ] **Step 7: Implement `PageController`**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/PageController.java`:

```java
package com.testingai.handlebars.controller;

import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

	private final SampleDataService sampleDataService;

	@GetMapping("/pages/products")
	public String products(Model model) {
		List<Product> products = sampleDataService.findAllProducts();
		model.addAttribute("products", products);
		return "products";
	}

	@GetMapping("/pages/orders/{id}")
	public String orderDetail(@PathVariable String id, Model model) {
		Order order = sampleDataService.findOrder(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
		model.addAttribute("order", order);
		return "order-detail";
	}
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn test -Dtest=PageControllerTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add template-engines/handlebars/spring-demo/src/main/resources/templates/ \
  template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/PageController.java \
  template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/PageControllerTest.java
git commit -m "feat(handlebars): add layout/products/order-detail templates and PageController"
```

---

### Task 6: Handlebars `DemoController` — all seven capability endpoints

**Files:**
- Create: `template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/DemoController.java`
- Test: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `Handlebars` bean (Task 4, injected), `SampleDataService` (Task 3).
- Produces: `GET /demo/variables`, `GET /demo/helpers/builtin`, `GET /demo/helpers/custom`, `GET /demo/partials`, `GET /demo/layout`, `GET /demo/subexpressions`, `GET /demo/precompiled` — each returns `text/html` body.

This task adds all seven endpoints and their tests in one pass — each is a small variation of the same `Handlebars.compileInline(...).apply(...)` pattern, so splitting further would mean rejecting one trivial endpoint while approving its neighbor.

- [ ] **Step 1: Write the failing tests**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/DemoControllerTest.java`:

```java
package com.testingai.handlebars.controller;

import com.testingai.handlebars.config.HandlebarsConfig;
import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.OrderItem;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import(HandlebarsConfig.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void variables_shouldEscapeDoubleStacheAndNotEscapeTripleStache() throws Exception {
		mockMvc.perform(get("/demo/variables")).andExpect(status().isOk())
				.andExpect(content().string(containsString("&lt;b&gt;Widget&lt;/b&gt;")))
				.andExpect(content().string(containsString("<b>Widget</b>")));
	}

	@Test
	void builtinHelpers_shouldMarkOutOfStockAndRenderCurrentOrder() throws Exception {
		when(sampleDataService.findAllProducts()).thenReturn(List.of(
				new Product("p1", "Widget", new BigDecimal("9.99"), 100),
				new Product("p3", "Gizmo", new BigDecimal("29.99"), 0)));
		Order order = new Order("o1", "Alice", List.of(), new BigDecimal("0"), "CONFIRMED",
				Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/demo/helpers/builtin")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget - in stock")))
				.andExpect(content().string(containsString("out of stock")))
				.andExpect(content().string(containsString("Order for Alice")));
	}

	@Test
	void customHelper_shouldFormatEachProductPriceAsCurrency() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/helpers/custom")).andExpect(status().isOk())
				.andExpect(content().string(containsString("$9.99")));
	}

	@Test
	void partials_shouldRenderOrderItemFragmentStandalone() throws Exception {
		Order order = new Order("o1", "Alice",
				List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98"))), new BigDecimal("19.98"),
				"CONFIRMED", Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/demo/partials")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("$19.98")));
	}

	@Test
	void layout_shouldInjectCustomBodyIntoSharedLayout() throws Exception {
		mockMvc.perform(get("/demo/layout")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Handlebars Demo")))
				.andExpect(content().string(containsString("Custom body content")));
	}

	@Test
	void subexpressions_shouldEvaluateNestedHelperCall() throws Exception {
		mockMvc.perform(get("/demo/subexpressions")).andExpect(status().isOk())
				.andExpect(content().string(containsString("$29.97")));
	}

	@Test
	void precompiled_shouldReportElapsedTimeForBothApproaches() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/precompiled")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Compiled 200 times")))
				.andExpect(content().string(containsString("Precompiled, applied 200 times")));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: COMPILATION FAILURE — `DemoController` does not exist yet.

- [ ] **Step 3: Implement `DemoController`**

`template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/DemoController.java`:

```java
package com.testingai.handlebars.controller;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.testingai.handlebars.model.Order;
import com.testingai.handlebars.model.Product;
import com.testingai.handlebars.service.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DemoController {

	private static final int PRECOMPILE_COMPARISON_ITERATIONS = 200;

	private final Handlebars handlebars;
	private final SampleDataService sampleDataService;

	@GetMapping("/demo/variables")
	public ResponseEntity<String> variables() throws IOException {
		Template template = handlebars.compileInline("<p>Escaped: {{name}}</p><p>Raw: {{{rawHtml}}}</p>");
		String rendered = template.apply(Map.of("name", "<b>Widget</b>", "rawHtml", "<b>Widget</b>"));
		return html(rendered);
	}

	@GetMapping("/demo/helpers/builtin")
	public ResponseEntity<String> builtinHelpers() throws IOException {
		String source = """
				<ul>
				{{#each products}}
				  <li>{{#if stock}}{{name}} - in stock{{else}}{{name}} - out of stock{{/if}}</li>
				{{/each}}
				</ul>
				{{#with customerOrder}}
				<p>Order for {{customer}}</p>
				{{/with}}
				""";
		Template template = handlebars.compileInline(source);
		Map<String, Object> context = Map.of("products", sampleDataService.findAllProducts(), "customerOrder",
				sampleDataService.findOrder("o1").orElseThrow());
		return html(template.apply(context));
	}

	@GetMapping("/demo/helpers/custom")
	public ResponseEntity<String> customHelper() throws IOException {
		String source = """
				<ul>
				{{#each products}}
				  <li>{{name}}: {{formatCurrency price}}</li>
				{{/each}}
				</ul>
				""";
		Template template = handlebars.compileInline(source);
		return html(template.apply(Map.of("products", sampleDataService.findAllProducts())));
	}

	@GetMapping("/demo/partials")
	public ResponseEntity<String> partials() throws IOException {
		Template template = handlebars.compile("partials/order-item");
		Order order = sampleDataService.findOrder("o1").orElseThrow();
		return html(template.apply(order.items().getFirst()));
	}

	@GetMapping("/demo/layout")
	public ResponseEntity<String> layout() throws IOException {
		String childSource = """
				{{#partial "body"}}
				<p>Custom body content injected into the shared layout.</p>
				{{/partial}}
				{{> layout}}
				""";
		Template template = handlebars.compileInline(childSource);
		return html(template.apply(null));
	}

	@GetMapping("/demo/subexpressions")
	public ResponseEntity<String> subexpressions() throws IOException {
		Template template = handlebars.compileInline("<p>Line total: {{formatCurrency (multiply price quantity)}}</p>");
		String rendered = template.apply(Map.of("price", new BigDecimal("9.99"), "quantity", new BigDecimal("3")));
		return html(rendered);
	}

	@GetMapping("/demo/precompiled")
	public ResponseEntity<String> precompiled() throws IOException {
		String source = "{{#each products}}{{name}}: {{formatCurrency price}} | {{/each}}";
		List<Product> products = sampleDataService.findAllProducts();
		Map<String, Object> context = Map.of("products", products);

		long compileEachTimeStart = System.nanoTime();
		for (int i = 0; i < PRECOMPILE_COMPARISON_ITERATIONS; i++) {
			handlebars.compileInline(source).apply(context);
		}
		long compileEachTimeNanos = System.nanoTime() - compileEachTimeStart;

		Template precompiledTemplate = handlebars.compileInline(source);
		long reuseStart = System.nanoTime();
		for (int i = 0; i < PRECOMPILE_COMPARISON_ITERATIONS; i++) {
			precompiledTemplate.apply(context);
		}
		long reuseNanos = System.nanoTime() - reuseStart;

		String body = String.format("<p>Compiled 200 times: %d ms</p><p>Precompiled, applied 200 times: %d ms</p>",
				compileEachTimeNanos / 1_000_000, reuseNanos / 1_000_000);
		return html(body);
	}

	private ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full module test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all prior test classes still passing.

- [ ] **Step 6: Commit**

```bash
git add template-engines/handlebars/spring-demo/src/main/java/com/testingai/handlebars/controller/DemoController.java \
  template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/controller/DemoControllerTest.java
git commit -m "feat(handlebars): add DemoController covering all seven capability endpoints"
```

---

### Task 7: Handlebars Gatling load test

**Files:**
- Create: `template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running `handlebars-demo` app on `localhost:8085` (Tasks 5 and 6's routes).

- [ ] **Step 1: Write the simulation**

`template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/performance/DemoSimulation.java`:

```java
package com.testingai.handlebars.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8085");

	private final ScenarioBuilder pagesScenario = scenario("Pages")
			.exec(http("Products Page").get("/pages/products").check(status().is(200)))
			.exec(http("Order Detail Page").get("/pages/orders/o1").check(status().is(200)));

	private final ScenarioBuilder capabilitiesScenario = scenario("Capabilities")
			.exec(http("Variables").get("/demo/variables").check(status().is(200)))
			.exec(http("Builtin Helpers").get("/demo/helpers/builtin").check(status().is(200)))
			.exec(http("Custom Helper").get("/demo/helpers/custom").check(status().is(200)))
			.exec(http("Partials").get("/demo/partials").check(status().is(200)))
			.exec(http("Layout").get("/demo/layout").check(status().is(200)))
			.exec(http("Subexpressions").get("/demo/subexpressions").check(status().is(200)))
			.exec(http("Precompiled").get("/demo/precompiled").check(status().is(200)));

	{
		setUp(pagesScenario.injectOpen(atOnceUsers(10)), capabilitiesScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it's excluded from the regular test run**

Run: `mvn test`
Expected: `BUILD SUCCESS`, surefire report does not mention `DemoSimulation` (excluded by the inherited `**/performance/**` surefire pattern).

- [ ] **Step 3: Commit**

```bash
git add template-engines/handlebars/spring-demo/src/test/java/com/testingai/handlebars/performance/
git commit -m "test(handlebars): add Gatling DemoSimulation"
```

---

### Task 8: Handlebars README

**Files:**
- Create: `template-engines/handlebars/spring-demo/README.md`

- [ ] **Step 1: Write the README**

`template-engines/handlebars/spring-demo/README.md`:

```markdown
# Handlebars Demo

A Spring Boot app demonstrating `com.github.jknack:handlebars` (Handlebars.java): variable escaping, built-in and custom helpers, partials, layout composition, subexpressions, and precompiled templates, around a product-catalog/orders domain. No external infrastructure required — pure in-process rendering.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `template-engines/handlebars/spring-demo/`.

## Run the app

```bash
mvn spring-boot:run
```

## Pages (real Spring MVC view resolution — open in a browser)

- http://localhost:8085/pages/products
- http://localhost:8085/pages/orders/o1
- http://localhost:8085/pages/orders/o2 (demonstrates `{{#unless status}}`, since `o2` has no status)

## Capability endpoints

```bash
# Variable substitution + HTML auto-escaping vs. {{{raw}}}
curl http://localhost:8085/demo/variables

# Built-in block helpers: #if / #unless / #each / #with
curl http://localhost:8085/demo/helpers/builtin

# Custom helper: formatCurrency
curl http://localhost:8085/demo/helpers/custom

# Partials: renders partials/order-item.hbs standalone
curl http://localhost:8085/demo/partials

# Partial-block layout composition
curl http://localhost:8085/demo/layout

# Subexpressions: {{formatCurrency (multiply price quantity)}}
curl http://localhost:8085/demo/subexpressions

# Precompiled template reuse vs. re-parsing per call (elapsed-time comparison)
curl http://localhost:8085/demo/precompiled
```

## Swagger UI

http://localhost:8085/swagger-ui/index.html

## Run performance tests

```bash
mvn gatling:test
```

Requires the app to already be running in a separate terminal.
```

- [ ] **Step 2: Commit**

```bash
git add template-engines/handlebars/spring-demo/README.md
git commit -m "docs(handlebars): add module README"
```

---

### Task 9: Scaffold the `freemarker-demo` module skeleton

**Files:**
- Create: `template-engines/freemarker/spring-demo/pom.xml`
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/FreemarkerDemoApplication.java`
- Create: `template-engines/freemarker/spring-demo/src/main/resources/application.yml`
- Test: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/FreemarkerDemoApplicationTest.java`

**Interfaces:**
- Produces: `com.testingai.freemarker.FreemarkerDemoApplication`, Maven coordinates `com.testingai:freemarker-demo`, server port `8086`.

- [ ] **Step 1: Create the module POM**

`template-engines/freemarker/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>template-engines</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>freemarker-demo</artifactId>
    <name>FreeMarker Demo</name>
    <description>Learning and demonstration project for Apache FreeMarker templating capabilities</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-freemarker</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.freemarker.FreemarkerDemoApplication</mainClass>
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
                    <simulationClass>com.testingai.freemarker.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the Spring Boot main class**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/FreemarkerDemoApplication.java`:

```java
package com.testingai.freemarker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FreemarkerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(FreemarkerDemoApplication.class, args);
	}
}
```

- [ ] **Step 3: Create `application.yml`**

`template-engines/freemarker/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8086

spring:
  freemarker:
    template-loader-path: classpath:/templates/
    suffix: .ftlh
```

- [ ] **Step 4: Write the application smoke test**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/FreemarkerDemoApplicationTest.java`:

```java
package com.testingai.freemarker;

import org.junit.jupiter.api.Test;

class FreemarkerDemoApplicationTest {

	@Test
	void mainClassExists() {
		new FreemarkerDemoApplication();
	}
}
```

- [ ] **Step 5: Build the reactor**

Run: `cd template-engines && mvn clean package`
Expected: `BUILD SUCCESS`, both `handlebars-demo` and `freemarker-demo` build, `FreemarkerDemoApplicationTest` passes.

- [ ] **Step 6: Commit**

```bash
git add template-engines/freemarker/spring-demo/pom.xml \
  template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/FreemarkerDemoApplication.java \
  template-engines/freemarker/spring-demo/src/main/resources/application.yml \
  template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/FreemarkerDemoApplicationTest.java
git commit -m "feat(freemarker): scaffold freemarker-demo module"
```

---

### Task 10: FreeMarker domain model and sample data

**Files:**
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/Product.java`
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/OrderItem.java`
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/Order.java`
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/service/SampleDataService.java`
- Test: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/service/SampleDataServiceTest.java`

**Interfaces:**
- Produces: same shape as the Handlebars module's Task 3 (`Product`, `OrderItem`, `Order`, `SampleDataService`), independent copy in package `com.testingai.freemarker.*` — consumed by `PageController` (Task 12) and `DemoController` (Task 13).

- [ ] **Step 1: Create the domain records**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/Product.java`:

```java
package com.testingai.freemarker.model;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, int stock) {
}
```

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/OrderItem.java`:

```java
package com.testingai.freemarker.model;

import java.math.BigDecimal;

public record OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal) {
}
```

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/Order.java`:

```java
package com.testingai.freemarker.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(String id, String customer, List<OrderItem> items, BigDecimal total, String status,
		Instant placedAt) {
}
```

- [ ] **Step 2: Write the failing test**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/service/SampleDataServiceTest.java`:

```java
package com.testingai.freemarker.service;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataServiceTest {

	private final SampleDataService service = new SampleDataService();

	@Test
	void findAllProducts_shouldReturnFourSeededProducts() {
		List<Product> products = service.findAllProducts();

		assertThat(products).hasSize(4);
		assertThat(products).extracting(Product::id).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	void findOrder_shouldReturnOrderWhenPresent() {
		Optional<Order> order = service.findOrder("o1");

		assertThat(order).isPresent();
		assertThat(order.get().customer()).isEqualTo("Alice");
	}

	@Test
	void findOrder_shouldReturnEmptyWhenMissing() {
		assertThat(service.findOrder("missing")).isEmpty();
	}

	@Test
	void findAllOrders_shouldReturnTwoSeededOrders() {
		assertThat(service.findAllOrders()).hasSize(2);
	}

	@Test
	void secondSeededOrder_shouldHaveNullStatusForNullSafetyDemos() {
		Order order = service.findOrder("o2").orElseThrow();

		assertThat(order.status()).isNull();
	}
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd template-engines/freemarker/spring-demo && mvn test -Dtest=SampleDataServiceTest`
Expected: COMPILATION FAILURE — `SampleDataService` does not exist yet.

- [ ] **Step 4: Implement `SampleDataService`**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/service/SampleDataService.java`:

```java
package com.testingai.freemarker.service;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.OrderItem;
import com.testingai.freemarker.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SampleDataService {

	private final List<Product> products = List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100),
			new Product("p2", "Gadget", new BigDecimal("19.99"), 50),
			new Product("p3", "Gizmo", new BigDecimal("29.99"), 0),
			new Product("p4", "Doohickey", new BigDecimal("4.99"), 200));

	private final List<Order> orders = List.of(
			new Order("o1", "Alice",
					List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98")),
							new OrderItem("p2", "Gadget", 1, new BigDecimal("19.99"))),
					new BigDecimal("39.97"), "CONFIRMED", Instant.parse("2026-07-01T10:15:30Z")),
			new Order("o2", "Bob", List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))),
					new BigDecimal("14.97"), null, Instant.parse("2026-07-05T08:00:00Z")));

	public List<Product> findAllProducts() {
		return products;
	}

	public Optional<Order> findOrder(String id) {
		return orders.stream().filter(order -> order.id().equals(id)).findFirst();
	}

	public List<Order> findAllOrders() {
		return orders;
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=SampleDataServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/model/ \
  template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/service/ \
  template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/service/
git commit -m "feat(freemarker): add Product/Order domain model and sample data"
```

---

### Task 11: `FreemarkerConfig` — standalone `Configuration` for ad hoc rendering

**Files:**
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/config/FreemarkerConfig.java`
- Test: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/config/FreemarkerConfigTest.java`

**Interfaces:**
- Produces: Spring bean `freemarker.template.Configuration demoFreemarkerConfiguration()` — `UTF-8` default encoding, `TemplateExceptionHandler.RETHROW_HANDLER` (so a template error surfaces as a `TemplateException` instead of Spring's default HTML-debug-comment output), UTC time zone (avoids date-formatting flakiness across machines), and a `ClassTemplateLoader` rooted at `/templates` so ad hoc templates can `#include`/`#import` the same files the MVC pages use. Consumed by `DemoController` (Task 13).
- This bean is entirely separate from Spring Boot's auto-configured `FreeMarkerConfigurer`/`FreeMarkerViewResolver` (Task 12) — the MVC page layer keeps using Spring Boot's defaults untouched; this bean exists only for `DemoController`'s ad hoc `Template` compilation.

- [ ] **Step 1: Write the failing test**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/config/FreemarkerConfigTest.java`:

```java
package com.testingai.freemarker.config;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreemarkerConfigTest {

	private final freemarker.template.Configuration configuration = new FreemarkerConfig()
			.demoFreemarkerConfiguration();

	@Test
	void configuration_shouldRethrowTemplateExceptionsInsteadOfEmbeddingHtmlDebugOutput() {
		assertThatThrownBy(() -> {
			Template template = new Template("broken", new StringReader("${missing.field}"), configuration);
			template.process(Map.of(), new StringWriter());
		}).isInstanceOf(TemplateException.class);
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=FreemarkerConfigTest`
Expected: COMPILATION FAILURE — `FreemarkerConfig` does not exist yet.

- [ ] **Step 3: Implement `FreemarkerConfig`**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/config/FreemarkerConfig.java`:

```java
package com.testingai.freemarker.config;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class FreemarkerConfig {

	@Bean
	public freemarker.template.Configuration demoFreemarkerConfiguration() {
		freemarker.template.Configuration configuration = new freemarker.template.Configuration(
				freemarker.template.Configuration.VERSION_2_3_31);
		configuration.setDefaultEncoding("UTF-8");
		configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		configuration.setLogTemplateExceptions(false);
		configuration.setTimeZone(TimeZone.getTimeZone("UTC"));
		configuration.setTemplateLoader(new ClassTemplateLoader(FreemarkerConfig.class, "/templates"));
		return configuration;
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=FreemarkerConfigTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/config/ \
  template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/config/
git commit -m "feat(freemarker): add FreemarkerConfig for ad hoc template rendering"
```

---

### Task 12: FreeMarker templates and `PageController`

**Files:**
- Create: `template-engines/freemarker/spring-demo/src/main/resources/templates/layout.ftlh`
- Create: `template-engines/freemarker/spring-demo/src/main/resources/templates/macros/product-row.ftlh`
- Create: `template-engines/freemarker/spring-demo/src/main/resources/templates/products.ftlh`
- Create: `template-engines/freemarker/spring-demo/src/main/resources/templates/order-detail.ftlh`
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/PageController.java`
- Test: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/PageControllerTest.java`

**Interfaces:**
- Consumes: `SampleDataService` (Task 10). Uses Spring Boot's auto-configured `FreeMarkerViewResolver`, not `FreemarkerConfig` (Task 11).
- Produces: `GET /pages/products` and `GET /pages/orders/{id}` (404 via `ResponseStatusException` when missing).
- Record fields are accessed with explicit method-call syntax (`product.name()`, not `product.name`) throughout — FreeMarker only exposes Java record components as no-parens properties starting from `incompatibleImprovements` `2.3.33`, and this module targets `2.3.31`-compatible syntax to avoid a version mismatch with whatever patch release Spring Boot 3.4.4 happens to bundle.

- [ ] **Step 1: Create the layout macro library**

`template-engines/freemarker/spring-demo/src/main/resources/templates/layout.ftlh`:

```freemarker
<#macro page title>
<!DOCTYPE html>
<html>
<head><title>${title}</title></head>
<body>
<header><h1>FreeMarker Demo</h1></header>
<main>
<#nested>
</main>
</body>
</html>
</#macro>
```

- [ ] **Step 2: Create the product-row macro**

`template-engines/freemarker/spring-demo/src/main/resources/templates/macros/product-row.ftlh`:

```freemarker
<#macro productRow item>
<li>${item.productName()} x ${item.quantity()} = $${item.lineTotal()?string("0.00")}</li>
</#macro>
```

- [ ] **Step 3: Create the products page**

`template-engines/freemarker/spring-demo/src/main/resources/templates/products.ftlh`:

```freemarker
<#import "layout.ftlh" as layout>
<@layout.page title="Products">
<table>
<tr><th>Name</th><th>Price</th><th>Stock</th></tr>
<#list products as product>
<tr><td>${product.name()}</td><td>$${product.price()?string("0.00")}</td><td>${product.stock()}</td></tr>
</#list>
</table>
</@layout.page>
```

- [ ] **Step 4: Create the order-detail page**

`template-engines/freemarker/spring-demo/src/main/resources/templates/order-detail.ftlh`:

```freemarker
<#import "layout.ftlh" as layout>
<#import "macros/product-row.ftlh" as rows>
<@layout.page title="Order ${order.id()}">
<h2>Order ${order.id()} for ${order.customer()}</h2>
<#if order.items()?has_content>
<ul>
<#list order.items() as item>
<@rows.productRow item=item/>
</#list>
</ul>
<#else>
<p>No items.</p>
</#if>
<p>Status: ${order.status()!"pending"}</p>
<p>Total: $${order.total()?string("0.00")}</p>
</@layout.page>
```

- [ ] **Step 5: Write the failing test**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/PageControllerTest.java`:

```java
package com.testingai.freemarker.controller;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.OrderItem;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageController.class)
class PageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void products_shouldRenderProductTableWithinLayout() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/pages/products")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("9.99")))
				.andExpect(content().string(containsString("FreeMarker Demo")));
	}

	@Test
	void orderDetail_shouldRenderOrderItemsWithinLayout() throws Exception {
		Order order = new Order("o1", "Alice",
				List.of(new OrderItem("p1", "Widget", 2, new BigDecimal("19.98"))), new BigDecimal("19.98"),
				"CONFIRMED", Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findOrder("o1")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o1")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Alice")))
				.andExpect(content().string(containsString("Widget")));
	}

	@Test
	void orderDetail_shouldRenderDefaultStatusWhenNull() throws Exception {
		Order order = new Order("o2", "Bob",
				List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))), new BigDecimal("14.97"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findOrder("o2")).thenReturn(Optional.of(order));

		mockMvc.perform(get("/pages/orders/o2")).andExpect(status().isOk())
				.andExpect(content().string(containsString("pending")));
	}

	@Test
	void orderDetail_shouldReturn404WhenOrderMissing() throws Exception {
		when(sampleDataService.findOrder("missing")).thenReturn(Optional.empty());

		mockMvc.perform(get("/pages/orders/missing")).andExpect(status().isNotFound());
	}
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `mvn test -Dtest=PageControllerTest`
Expected: COMPILATION FAILURE — `PageController` does not exist yet.

- [ ] **Step 7: Implement `PageController`**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/PageController.java`:

```java
package com.testingai.freemarker.controller;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

	private final SampleDataService sampleDataService;

	@GetMapping("/pages/products")
	public String products(Model model) {
		List<Product> products = sampleDataService.findAllProducts();
		model.addAttribute("products", products);
		return "products";
	}

	@GetMapping("/pages/orders/{id}")
	public String orderDetail(@PathVariable String id, Model model) {
		Order order = sampleDataService.findOrder(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
		model.addAttribute("order", order);
		return "order-detail";
	}
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn test -Dtest=PageControllerTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add template-engines/freemarker/spring-demo/src/main/resources/templates/ \
  template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/PageController.java \
  template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/PageControllerTest.java
git commit -m "feat(freemarker): add layout/products/order-detail templates and PageController"
```

---

### Task 13: FreeMarker `DemoController` — all eight capability endpoints

**Files:**
- Create: `template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/DemoController.java`
- Test: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `freemarker.template.Configuration` bean named `demoFreemarkerConfiguration` (Task 11, injected), `SampleDataService` (Task 10).
- Produces: `GET /demo/data-model`, `GET /demo/directives/if-list`, `GET /demo/directives/switch`, `GET /demo/macros`, `GET /demo/functions`, `GET /demo/builtins`, `GET /demo/composition`, `GET /demo/null-safety` — each returns `text/html` body.

All eight endpoints and their tests are added in this single task, for the same reason as Task 6: each is a small variation of the same "compile an ad hoc `Template`, `process` it, return the `StringWriter`" pattern.

- [ ] **Step 1: Write the failing tests**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/DemoControllerTest.java`:

```java
package com.testingai.freemarker.controller;

import com.testingai.freemarker.config.FreemarkerConfig;
import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.OrderItem;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@Import(FreemarkerConfig.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SampleDataService sampleDataService;

	@Test
	void dataModel_shouldRenderBothRecordAndMapAccess() throws Exception {
		mockMvc.perform(get("/demo/data-model")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Record (method-call access)")))
				.andExpect(content().string(containsString("Map (property access)")))
				.andExpect(content().string(containsString("9.99")));
	}

	@Test
	void ifList_shouldMarkOutOfStockProducts() throws Exception {
		when(sampleDataService.findAllProducts()).thenReturn(List.of(
				new Product("p1", "Widget", new BigDecimal("9.99"), 100),
				new Product("p3", "Gizmo", new BigDecimal("29.99"), 0)));

		mockMvc.perform(get("/demo/directives/if-list")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget - in stock")))
				.andExpect(content().string(containsString("Gizmo - out of stock")));
	}

	@Test
	void switchDirective_shouldMapConfirmedStatusToShippingMessage() throws Exception {
		Order confirmed = new Order("o1", "Alice", List.of(), new BigDecimal("0"), "CONFIRMED",
				Instant.parse("2026-07-01T10:15:30Z"));
		when(sampleDataService.findAllOrders()).thenReturn(List.of(confirmed));

		mockMvc.perform(get("/demo/directives/switch")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Confirmed and ready to ship")));
	}

	@Test
	void switchDirective_shouldFallBackToDefaultCaseWhenStatusNull() throws Exception {
		Order pending = new Order("o2", "Bob", List.of(), new BigDecimal("0"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findAllOrders()).thenReturn(List.of(pending));

		mockMvc.perform(get("/demo/directives/switch")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Awaiting confirmation")));
	}

	@Test
	void macros_shouldRenderOneRowPerProduct() throws Exception {
		when(sampleDataService.findAllProducts())
				.thenReturn(List.of(new Product("p1", "Widget", new BigDecimal("9.99"), 100)));

		mockMvc.perform(get("/demo/macros")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Widget")))
				.andExpect(content().string(containsString("9.99")));
	}

	@Test
	void functions_shouldApplyDiscountViaUserDefinedFunction() throws Exception {
		mockMvc.perform(get("/demo/functions")).andExpect(status().isOk())
				.andExpect(content().string(containsString("8.99")));
	}

	@Test
	void builtins_shouldFormatStringNumberAndDate() throws Exception {
		mockMvc.perform(get("/demo/builtins")).andExpect(status().isOk())
				.andExpect(content().string(containsString("WIDGET")))
				.andExpect(content().string(containsString("9.90")))
				.andExpect(content().string(containsString("2026-07-01")));
	}

	@Test
	void composition_shouldReuseTheSharedLayoutMacro() throws Exception {
		mockMvc.perform(get("/demo/composition")).andExpect(status().isOk())
				.andExpect(content().string(containsString("FreeMarker Demo")))
				.andExpect(content().string(containsString("Composed Fragment")));
	}

	@Test
	void nullSafety_shouldApplyDefaultOperatorWhenStatusMissing() throws Exception {
		Order sparseOrder = new Order("o2", "Bob",
				List.of(new OrderItem("p4", "Doohickey", 3, new BigDecimal("14.97"))), new BigDecimal("14.97"), null,
				Instant.parse("2026-07-05T08:00:00Z"));
		when(sampleDataService.findOrder("o2")).thenReturn(Optional.of(sparseOrder));

		mockMvc.perform(get("/demo/null-safety")).andExpect(status().isOk())
				.andExpect(content().string(containsString("pending")))
				.andExpect(content().string(containsString("no")));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: COMPILATION FAILURE — `DemoController` does not exist yet.

- [ ] **Step 3: Implement `DemoController`**

`template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/DemoController.java`:

```java
package com.testingai.freemarker.controller;

import com.testingai.freemarker.model.Order;
import com.testingai.freemarker.model.Product;
import com.testingai.freemarker.service.SampleDataService;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DemoController {

	private final freemarker.template.Configuration demoFreemarkerConfiguration;
	private final SampleDataService sampleDataService;

	@GetMapping("/demo/data-model")
	public ResponseEntity<String> dataModel() throws IOException, TemplateException {
		Product product = new Product("p1", "Widget", new BigDecimal("9.99"), 100);
		String recordSource = "<p>${product.name()}: $${product.price()?string(\"0.00\")}</p>";
		String fromRecord = render("data-model-record", recordSource, Map.of("product", product));

		Map<String, Object> productAsMap = Map.of("name", "Widget", "price", new BigDecimal("9.99"));
		String mapSource = "<p>${product.name}: $${product.price?string(\"0.00\")}</p>";
		String fromMap = render("data-model-map", mapSource, Map.of("product", productAsMap));

		return html("<h3>Record (method-call access)</h3>" + fromRecord + "<h3>Map (property access)</h3>" + fromMap);
	}

	@GetMapping("/demo/directives/if-list")
	public ResponseEntity<String> ifList() throws IOException, TemplateException {
		String source = """
				<ul>
				<#list products as product>
				  <li><#if product.stock() gt 0>${product.name()} - in stock (${product.stock()})<#else>${product.name()} - out of stock</#if></li>
				</#list>
				</ul>
				""";
		String rendered = render("if-list", source, Map.of("products", sampleDataService.findAllProducts()));
		return html(rendered);
	}

	@GetMapping("/demo/directives/switch")
	public ResponseEntity<String> switchDirective() throws IOException, TemplateException {
		String source = """
				<#list orders as order>
				  <p>Order ${order.id()}:
				  <#switch order.status()!"UNKNOWN">
				    <#case "CONFIRMED">Confirmed and ready to ship<#break>
				    <#case "CANCELLED">Cancelled<#break>
				    <#default>Awaiting confirmation
				  </#switch>
				  </p>
				</#list>
				""";
		String rendered = render("switch", source, Map.of("orders", sampleDataService.findAllOrders()));
		return html(rendered);
	}

	@GetMapping("/demo/macros")
	public ResponseEntity<String> macros() throws IOException, TemplateException {
		String source = """
				<#macro productRow product>
				  <tr><td>${product.name()}</td><td>$${product.price()?string("0.00")}</td></tr>
				</#macro>
				<table>
				<#list products as product>
				  <@productRow product=product/>
				</#list>
				</table>
				""";
		String rendered = render("macros", source, Map.of("products", sampleDataService.findAllProducts()));
		return html(rendered);
	}

	@GetMapping("/demo/functions")
	public ResponseEntity<String> functions() throws IOException, TemplateException {
		String source = """
				<#function discountedPrice price percentOff>
				  <#return price - (price * percentOff / 100)>
				</#function>
				<p>Discounted price: $${discountedPrice(product.price(), 10)?string("0.00")}</p>
				""";
		Product product = new Product("p1", "Widget", new BigDecimal("9.99"), 100);
		String rendered = render("functions", source, Map.of("product", product));
		return html(rendered);
	}

	@GetMapping("/demo/builtins")
	public ResponseEntity<String> builtins() throws IOException, TemplateException {
		String source = """
				<p>Upper: ${name?upper_case}</p>
				<p>Price: $${price?string("0.00")}</p>
				<p>Placed at: ${placedAt?string("yyyy-MM-dd")}</p>
				""";
		Map<String, Object> model = Map.of("name", "widget", "price", new BigDecimal("9.9"), "placedAt",
				Date.from(Instant.parse("2026-07-01T10:15:30Z")));
		String rendered = render("builtins", source, model);
		return html(rendered);
	}

	@GetMapping("/demo/composition")
	public ResponseEntity<String> composition() throws IOException, TemplateException {
		String source = """
				<#import "layout.ftlh" as layout>
				<@layout.page title="Composed Fragment">
				<p>This fragment reuses the same layout.ftlh macro the MVC pages use, via #import.</p>
				</@layout.page>
				""";
		String rendered = render("composition", source, Map.of());
		return html(rendered);
	}

	@GetMapping("/demo/null-safety")
	public ResponseEntity<String> nullSafety() throws IOException, TemplateException {
		String source = """
				<p>Status (default operator): ${order.status()!"pending"}</p>
				<p>Status exists? <#if order.status()??>yes<#else>no</#if></p>
				""";
		Order sparseOrder = sampleDataService.findOrder("o2").orElseThrow();
		String rendered = render("null-safety", source, Map.of("order", sparseOrder));
		return html(rendered);
	}

	private String render(String templateName, String source, Object dataModel) throws IOException, TemplateException {
		Template template = new Template(templateName, new StringReader(source), demoFreemarkerConfiguration);
		StringWriter writer = new StringWriter();
		template.process(dataModel, writer);
		return writer.toString();
	}

	private ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
	}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full module test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all prior test classes still passing.

- [ ] **Step 6: Commit**

```bash
git add template-engines/freemarker/spring-demo/src/main/java/com/testingai/freemarker/controller/DemoController.java \
  template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/controller/DemoControllerTest.java
git commit -m "feat(freemarker): add DemoController covering all eight capability endpoints"
```

---

### Task 14: FreeMarker Gatling load test

**Files:**
- Create: `template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running `freemarker-demo` app on `localhost:8086` (Tasks 12 and 13's routes).

- [ ] **Step 1: Write the simulation**

`template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/performance/DemoSimulation.java`:

```java
package com.testingai.freemarker.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8086");

	private final ScenarioBuilder pagesScenario = scenario("Pages")
			.exec(http("Products Page").get("/pages/products").check(status().is(200)))
			.exec(http("Order Detail Page").get("/pages/orders/o1").check(status().is(200)));

	private final ScenarioBuilder capabilitiesScenario = scenario("Capabilities")
			.exec(http("Data Model").get("/demo/data-model").check(status().is(200)))
			.exec(http("If List").get("/demo/directives/if-list").check(status().is(200)))
			.exec(http("Switch").get("/demo/directives/switch").check(status().is(200)))
			.exec(http("Macros").get("/demo/macros").check(status().is(200)))
			.exec(http("Functions").get("/demo/functions").check(status().is(200)))
			.exec(http("Builtins").get("/demo/builtins").check(status().is(200)))
			.exec(http("Composition").get("/demo/composition").check(status().is(200)))
			.exec(http("Null Safety").get("/demo/null-safety").check(status().is(200)));

	{
		setUp(pagesScenario.injectOpen(atOnceUsers(10)), capabilitiesScenario.injectOpen(atOnceUsers(10)))
				.protocols(httpProtocol).maxDuration(Duration.ofSeconds(30));
	}
}
```

- [ ] **Step 2: Verify it's excluded from the regular test run**

Run: `mvn test`
Expected: `BUILD SUCCESS`, surefire report does not mention `DemoSimulation`.

- [ ] **Step 3: Commit**

```bash
git add template-engines/freemarker/spring-demo/src/test/java/com/testingai/freemarker/performance/
git commit -m "test(freemarker): add Gatling DemoSimulation"
```

---

### Task 15: FreeMarker README

**Files:**
- Create: `template-engines/freemarker/spring-demo/README.md`

- [ ] **Step 1: Write the README**

`template-engines/freemarker/spring-demo/README.md`:

```markdown
# FreeMarker Demo

A Spring Boot app demonstrating Apache FreeMarker: data-model binding (POJO/record vs. `Map`), `#if`/`#list`/`#switch` directives, user-defined macros and functions, built-ins, `#include`/`#import` composition, and null-safety operators, around a product-catalog/orders domain. No external infrastructure required — pure in-process rendering.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `template-engines/freemarker/spring-demo/`.

## Run the app

```bash
mvn spring-boot:run
```

## Pages (real Spring MVC view resolution — open in a browser)

- http://localhost:8086/pages/products
- http://localhost:8086/pages/orders/o1
- http://localhost:8086/pages/orders/o2 (demonstrates the `!"pending"` default-value operator, since `o2` has no status)

## Capability endpoints

```bash
# Data-model binding: same rendering logic against a record vs. a Map
curl http://localhost:8086/demo/data-model

# #if / #list directives
curl http://localhost:8086/demo/directives/if-list

# #switch / #case / #default
curl http://localhost:8086/demo/directives/switch

# User-defined macro
curl http://localhost:8086/demo/macros

# User-defined function
curl http://localhost:8086/demo/functions

# Built-ins: ?upper_case, ?string number/date formatting
curl http://localhost:8086/demo/builtins

# #import composition, reusing the same layout.ftlh macro the MVC pages use
curl http://localhost:8086/demo/composition

# Null-safety operators: ! (default) and ?? (exists)
curl http://localhost:8086/demo/null-safety
```

## Swagger UI

http://localhost:8086/swagger-ui/index.html

## Run performance tests

```bash
mvn gatling:test
```

Requires the app to already be running in a separate terminal.
```

- [ ] **Step 2: Commit**

```bash
git add template-engines/freemarker/spring-demo/README.md
git commit -m "docs(freemarker): add module README"
```

---

### Task 16: Cross-cutting wiring — git hook, `CLAUDE.md`, top-level README, full reactor build

**Files:**
- Modify: `.githooks/pre-commit`
- Modify: `CLAUDE.md`
- Modify: `template-engines/README.md`

**Interfaces:**
- No new production code — this task wires the new reactor into repo-wide tooling and does a final end-to-end verification.

- [ ] **Step 1: Extend the pre-commit hook**

In `.githooks/pre-commit`, change:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing)/.*\.java$' || true)
```

to:

```bash
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines)/.*\.java$' || true)
```

and add a new block right after the `cqrs-event-sourcing` block:

```bash
if echo "$STAGED_JAVA" | grep -q '^template-engines/'; then
    echo "[pre-commit] Applying Spotless formatting to staged template-engines Java files..."
    (cd "$ROOT/template-engines" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 2: Update `CLAUDE.md`**

Add a new "Template engine demos" subsection under the `## Commands` heading, right after the "CQRS/Event Sourcing demos" section and before "Backend REST API":

```markdown
### Template engine demos (both modules — run from the module root, no docker infrastructure required)

```bash
cd template-engines/<engine>/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```
```

Add a new row to the repository layout table (after the `cqrs-event-sourcing/<framework>/spring-demo/` row):

```markdown
| `template-engines/<engine>/spring-demo/` | Template-engine demo apps, same conventions as `message-brokers/` (currently: Handlebars, FreeMarker) — no external infrastructure required |
```

- [ ] **Step 3: Finalize `template-engines/README.md`**

Confirm the table written in Task 1 Step 3 already lists both engines with their "best fit" descriptions — no change needed if Task 1's content matches; otherwise update it to match the two README files written in Tasks 8 and 15.

- [ ] **Step 4: Full reactor build**

Run: `cd template-engines && mvn clean package`
Expected: `BUILD SUCCESS` for both `handlebars-demo` and `freemarker-demo`.

- [ ] **Step 5: Manual smoke test — Handlebars**

Run: `cd template-engines/handlebars/spring-demo && mvn spring-boot:run` (separate terminal)

Then:
```bash
curl -s http://localhost:8085/pages/products | grep -o 'Widget'
curl -s http://localhost:8085/demo/subexpressions
```
Expected: `Widget` printed once; the subexpressions call returns `<p>Line total: $29.97</p>`.

Stop the app (Ctrl+C).

- [ ] **Step 6: Manual smoke test — FreeMarker**

Run: `cd template-engines/freemarker/spring-demo && mvn spring-boot:run` (separate terminal)

Then:
```bash
curl -s http://localhost:8086/pages/products | grep -o 'Widget'
curl -s http://localhost:8086/demo/null-safety
```
Expected: `Widget` printed once; the null-safety call shows `pending` and `no`.

Stop the app (Ctrl+C).

- [ ] **Step 7: Verify Spotless formatting is clean**

Run: `cd template-engines && mvn spotless:check`
Expected: `BUILD SUCCESS` (no formatting violations). If it fails, run `mvn spotless:apply` and re-verify.

- [ ] **Step 8: Commit**

```bash
git add .githooks/pre-commit CLAUDE.md template-engines/README.md
git commit -m "chore(template-engines): wire pre-commit hook, CLAUDE.md, and top-level README"
```
