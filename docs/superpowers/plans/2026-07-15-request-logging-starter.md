# Request-Logging Spring Boot Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `spring-boot-starters/` Maven reactor containing the repo's first custom Spring Boot starter — `request-logging` — split into two modules: `request-logging-spring-boot-starter` (a reusable auto-configuration jar that logs every HTTP request/response) and `spring-demo` (a runnable Spring Boot app that consumes the starter and proves it works end-to-end).

**Architecture:** `request-logging-spring-boot-starter` exposes `RequestLoggingProperties` (`@ConfigurationProperties`), `RequestLoggingFilter` (a plain `OncePerRequestFilter`), and `RequestLoggingAutoConfiguration` (`@AutoConfiguration`, registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`). The filter always logs method/path/status/duration for non-excluded paths, and additionally logs (truncated) request/response bodies when `include-body` is enabled, using `ContentCachingRequestWrapper`/`ContentCachingResponseWrapper`. `spring-demo` depends on the starter as a normal Maven dependency (not just a shared parent) and exposes two endpoints (`/demo/hello`, `/demo/echo`) that make the filter's behavior observable.

**Tech Stack:** Spring Boot 3.4.4, Java 21, springdoc-openapi (demo only), JUnit 5 + AssertJ, Spring Boot Test (`ApplicationContextRunner`, `MockMvc`, mock servlet classes), Logback `ListAppender` for log-assertion tests. No Lombok, no Gatling — see Global Constraints for why.

## Global Constraints

- Java 21, Spring Boot 3.4.4 (matches every other module in this repo).
- `spring-demo` depends on `request-logging-spring-boot-starter` as a Maven dependency, not just a shared parent — unlike every other category in this repo. This only resolves without a prior `mvn install` if both modules build inside the same reactor, so every build/run/test command in this plan is run from `spring-boot-starters/` (the category root), never from inside `spring-demo/` alone.
- No Lombok dependency anywhere in this reactor — neither module has a class with enough fields/constructor-injection to benefit from it (the filter takes one constructor argument; the demo controller has no fields at all). This deliberately deviates from the Lombok-by-default convention in other categories.
- No Gatling / performance test — this category is about auto-configuration behavior, not throughput. The parent POM keeps the `**/performance/**` surefire exclude for consistency with sibling parent POMs, even though no file currently lands there.
- No `util/FailureSimulator` — nothing here simulates random failures.
- Prefer records, `@DefaultValue` constructor binding, pattern matching, and text blocks over pre-Java-21 idioms, on any line this plan adds.
- No explicit `.toString()` on values passed to SLF4J `{}` placeholders or string concatenation.
- All instance fields assigned once must be `private final`.
- Formatting is enforced by Spotless (`spotless:apply`, wired into `spring-boot-starters/pom.xml`'s git hook) — do not hand-format; let Spotless reformat on commit.
- App port is `8090` (next free slot after `distributed-transactions/saga`'s `8089`).

---

### Task 1: Scaffold the `spring-boot-starters/` Maven reactor

**Files:**
- Create: `spring-boot-starters/pom.xml`
- Create: `spring-boot-starters/eclipse-formatter.xml`
- Create: `spring-boot-starters/README.md`

**Interfaces:**
- Produces: Maven parent coordinates `com.testingai:spring-boot-starters:1.0.0` (packaging `pom`), properties `springdoc.version`, `spotless.version` — consumed by both leaf modules' POMs (Task 2).

- [ ] **Step 1: Create the parent POM**

`spring-boot-starters/pom.xml`:

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
    <artifactId>spring-boot-starters</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Spring Boot Starters</name>
    <description>Parent POM for all custom Spring Boot starter demo modules</description>

    <modules>
        <module>request-logging/request-logging-spring-boot-starter</module>
        <module>request-logging/spring-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <springdoc.version>2.8.6</springdoc.version>
        <spotless.version>2.43.0</spotless.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-Dnet.bytebuddy.experimental=true</argLine>
                    <excludes>
                        <exclude>**/performance/**</exclude>
                    </excludes>
                </configuration>
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

`spring-boot-starters/eclipse-formatter.xml` — identical content to `distributed-transactions/eclipse-formatter.xml`:

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

- [ ] **Step 3: Write a placeholder top-level README (finalized in Task 6)**

`spring-boot-starters/README.md`:

```markdown
# Spring Boot Starters — Demos

This directory contains runnable demonstrations of custom Spring Boot starters — the same
auto-configuration mechanism used by `spring-boot-starter-web` / `spring-boot-starter-data-jpa`,
applied to small features you might actually want to share across services.

| Starter | Demo | What it auto-configures |
|---|---|---|
| [`request-logging`](request-logging/) | `request-logging/spring-demo` | A servlet filter that logs every HTTP request/response |

More starters may be added here over time.
```

- [ ] **Step 4: Commit**

```bash
git add spring-boot-starters/pom.xml spring-boot-starters/eclipse-formatter.xml spring-boot-starters/README.md
git commit -m "feat(spring-boot-starters): scaffold spring-boot-starters Maven reactor"
```

---

### Task 2: Scaffold both leaf module skeletons

**Files:**
- Create: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/pom.xml`
- Create: `spring-boot-starters/request-logging/spring-demo/pom.xml`
- Create: `spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/RequestLoggingDemoApplication.java`
- Create: `spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml`
- Test: `spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingDemoApplicationTest.java`

**Interfaces:**
- Produces: Maven coordinates `com.testingai:request-logging-spring-boot-starter:1.0.0` (packaging `jar`, no main class) and `com.testingai:request-logging-demo:1.0.0` (executable), `com.testingai.logging.demo.RequestLoggingDemoApplication` (Spring Boot main class), server port `8090`.

- [ ] **Step 1: Create the starter module's POM**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>spring-boot-starters</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>request-logging-spring-boot-starter</artifactId>
    <name>Request Logging Spring Boot Starter</name>
    <description>Auto-configures a servlet filter that logs HTTP request/response method, path, status, and duration</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create the demo module's POM**

`spring-boot-starters/request-logging/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>spring-boot-starters</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>request-logging-demo</artifactId>
    <name>Request Logging Starter Demo</name>
    <description>Demo Spring Boot app consuming request-logging-spring-boot-starter to show its auto-configuration in action</description>

    <dependencies>
        <dependency>
            <groupId>com.testingai</groupId>
            <artifactId>request-logging-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.logging.demo.RequestLoggingDemoApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create the Spring Boot main class**

`spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/RequestLoggingDemoApplication.java`:

```java
package com.testingai.logging.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RequestLoggingDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RequestLoggingDemoApplication.class, args);
	}
}
```

- [ ] **Step 4: Create `application.yml`**

`spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8090
```

- [ ] **Step 5: Write the application smoke test**

`spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingDemoApplicationTest.java`:

```java
package com.testingai.logging.demo;

import org.junit.jupiter.api.Test;

class RequestLoggingDemoApplicationTest {

	@Test
	void mainClassExists() {
		new RequestLoggingDemoApplication();
	}
}
```

- [ ] **Step 6: Build the reactor**

Run: `cd spring-boot-starters && mvn clean package`
Expected: `BUILD SUCCESS`, both modules built (`request-logging-spring-boot-starter` as a plain jar, `request-logging-demo` as an executable jar), with `RequestLoggingDemoApplicationTest` reported passing.

- [ ] **Step 7: Commit**

```bash
git add spring-boot-starters/request-logging/request-logging-spring-boot-starter/pom.xml \
  spring-boot-starters/request-logging/spring-demo/pom.xml \
  spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/RequestLoggingDemoApplication.java \
  spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml \
  spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingDemoApplicationTest.java
git commit -m "feat(spring-boot-starters): scaffold request-logging module skeletons"
```

---

### Task 3: `RequestLoggingProperties` and `RequestLoggingFilter`

**Files:**
- Create: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingProperties.java`
- Create: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingFilter.java`
- Test: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingFilterTest.java`

**Interfaces:**
- Produces: `record RequestLoggingProperties(boolean enabled, boolean includeBody, List<String> excludedPaths)` with defaults `true` / `false` / `["/actuator/**"]`; `class RequestLoggingFilter extends OncePerRequestFilter` with constructor `RequestLoggingFilter(RequestLoggingProperties properties)` — consumed by `RequestLoggingAutoConfiguration` (Task 4).

- [ ] **Step 1: Write the failing filter tests**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingFilterTest.java`:

```java
package com.testingai.logging.autoconfigure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

	@Test
	void excludedPath_shouldSkipLoggingAndPassRequestThroughUnwrapped() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, true, List.of("/actuator/**"));
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		try {
			filter.doFilter(request, response, chain);

			assertThat(chain.getRequest()).isSameAs(request);
			assertThat(appender.list).isEmpty();
		} finally {
			detachAppender(appender);
		}
	}

	@Test
	void includedPath_withoutBodyLogging_shouldLogMethodPathAndStatus() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, false, List.of());
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/hello");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(200);
		MockFilterChain chain = new MockFilterChain();

		try {
			filter.doFilter(request, response, chain);

			assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("GET")
					&& event.getFormattedMessage().contains("/demo/hello")
					&& event.getFormattedMessage().contains("200"));
		} finally {
			detachAppender(appender);
		}
	}

	@Test
	void includedPath_withBodyLogging_shouldStillDeliverResponseBodyToClient() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, true, List.of());
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/demo/echo");
		request.setContent("{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.setStatus(200);
				resp.getWriter().write("{\"message\":\"hi\"}");
			}
		});

		filter.doFilter(request, response, chain);

		assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"hi\"}");
		assertThat(response.getStatus()).isEqualTo(200);
	}

	private ListAppender<ILoggingEvent> attachAppender() {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}

	private void detachAppender(ListAppender<ILoggingEvent> appender) {
		((Logger) LoggerFactory.getLogger(RequestLoggingFilter.class)).detachAppender(appender);
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd spring-boot-starters && mvn test -pl request-logging/request-logging-spring-boot-starter -Dtest=RequestLoggingFilterTest`
Expected: FAIL — compilation error, `RequestLoggingProperties` and `RequestLoggingFilter` do not exist yet.

- [ ] **Step 3: Implement `RequestLoggingProperties`**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingProperties.java`:

```java
package com.testingai.logging.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("app.logging.request")
public record RequestLoggingProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("false") boolean includeBody, @DefaultValue("/actuator/**") List<String> excludedPaths) {
}
```

- [ ] **Step 4: Implement `RequestLoggingFilter`**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingFilter.java`:

```java
package com.testingai.logging.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final int MAX_LOGGED_BODY_LENGTH = 1000;

	private final RequestLoggingProperties properties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public RequestLoggingFilter(RequestLoggingProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (isExcluded(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}
		if (properties.includeBody()) {
			doFilterWithBodyLogging(request, response, filterChain);
		} else {
			doFilterWithoutBodyLogging(request, response, filterChain);
		}
	}

	private boolean isExcluded(String path) {
		return properties.excludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
	}

	private void doFilterWithoutBodyLogging(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long start = System.currentTimeMillis();
		filterChain.doFilter(request, response);
		long durationMs = System.currentTimeMillis() - start;
		log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
	}

	private void doFilterWithBodyLogging(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		long start = System.currentTimeMillis();
		try {
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			long durationMs = System.currentTimeMillis() - start;
			String requestBody = truncatedBody(wrappedRequest.getContentAsByteArray());
			String responseBody = truncatedBody(wrappedResponse.getContentAsByteArray());
			log.info("{} {} -> {} ({} ms) requestBody={} responseBody={}", request.getMethod(),
					request.getRequestURI(), wrappedResponse.getStatus(), durationMs, requestBody, responseBody);
			wrappedResponse.copyBodyToResponse();
		}
	}

	private String truncatedBody(byte[] content) {
		if (content.length == 0) {
			return "";
		}
		int length = Math.min(content.length, MAX_LOGGED_BODY_LENGTH);
		return new String(content, 0, length, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd spring-boot-starters && mvn test -pl request-logging/request-logging-spring-boot-starter -Dtest=RequestLoggingFilterTest`
Expected: PASS, all 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingProperties.java \
  spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingFilter.java \
  spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingFilterTest.java
git commit -m "feat(spring-boot-starters): add RequestLoggingProperties and RequestLoggingFilter"
```

---

### Task 4: `RequestLoggingAutoConfiguration`

**Files:**
- Create: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfiguration.java`
- Create: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `RequestLoggingProperties`, `RequestLoggingFilter` (Task 3).
- Produces: `RequestLoggingAutoConfiguration` auto-registered via `AutoConfiguration.imports` — this is the entry point `spring-demo` (Task 5) picks up automatically with zero explicit wiring.

- [ ] **Step 1: Write the failing auto-configuration tests**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfigurationTest.java`:

```java
package com.testingai.logging.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class));

	@Test
	void filterRegisteredByDefault_withDefaultProperties() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RequestLoggingFilter.class);
			RequestLoggingProperties properties = context.getBean(RequestLoggingProperties.class);
			assertThat(properties.enabled()).isTrue();
			assertThat(properties.includeBody()).isFalse();
			assertThat(properties.excludedPaths()).containsExactly("/actuator/**");
		});
	}

	@Test
	void filterAbsentWhenDisabled() {
		contextRunner.withPropertyValues("app.logging.request.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
	}

	@Test
	void backsOffWhenUserProvidesOwnFilterBean() {
		contextRunner.withUserConfiguration(CustomFilterConfig.class).run(context -> {
			assertThat(context).hasSingleBean(RequestLoggingFilter.class);
			assertThat(context.getBean(RequestLoggingFilter.class)).isSameAs(CustomFilterConfig.CUSTOM_FILTER);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomFilterConfig {

		static final RequestLoggingFilter CUSTOM_FILTER = new RequestLoggingFilter(
				new RequestLoggingProperties(true, false, List.of()));

		@Bean
		RequestLoggingFilter requestLoggingFilter() {
			return CUSTOM_FILTER;
		}
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd spring-boot-starters && mvn test -pl request-logging/request-logging-spring-boot-starter -Dtest=RequestLoggingAutoConfigurationTest`
Expected: FAIL — compilation error, `RequestLoggingAutoConfiguration` does not exist yet.

- [ ] **Step 3: Implement `RequestLoggingAutoConfiguration`**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfiguration.java`:

```java
package com.testingai.logging.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RequestLoggingProperties.class)
public class RequestLoggingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "app.logging.request", name = "enabled", matchIfMissing = true)
	public RequestLoggingFilter requestLoggingFilter(RequestLoggingProperties properties) {
		return new RequestLoggingFilter(properties);
	}
}
```

- [ ] **Step 4: Register the auto-configuration**

`spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.testingai.logging.autoconfigure.RequestLoggingAutoConfiguration
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd spring-boot-starters && mvn test -pl request-logging/request-logging-spring-boot-starter -Dtest=RequestLoggingAutoConfigurationTest`
Expected: PASS, all 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfiguration.java \
  spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  spring-boot-starters/request-logging/request-logging-spring-boot-starter/src/test/java/com/testingai/logging/autoconfigure/RequestLoggingAutoConfigurationTest.java
git commit -m "feat(spring-boot-starters): add RequestLoggingAutoConfiguration"
```

---

### Task 5: Demo `DemoController` and end-to-end `RequestLoggingIntegrationTest`

**Files:**
- Modify: `spring-boot-starters/request-logging/spring-demo/pom.xml`
- Create: `spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/DemoController.java`
- Create: `spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/EchoRequest.java`
- Modify: `spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml`
- Test: `spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingIntegrationTest.java`

**Interfaces:**
- Consumes: `RequestLoggingAutoConfiguration`, `RequestLoggingFilter` (Task 4) — picked up with zero explicit wiring, purely by being on the classpath.
- Produces: `GET /demo/hello`, `POST /demo/echo` (body: `EchoRequest{message}`), `GET /actuator/health`.

- [ ] **Step 1: Add `actuator` and `springdoc` dependencies to the demo POM**

Edit `spring-boot-starters/request-logging/spring-demo/pom.xml` — add inside `<dependencies>`, right after the `request-logging-spring-boot-starter` dependency:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
```

- [ ] **Step 2: Write the failing integration test**

`spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingIntegrationTest.java`:

```java
package com.testingai.logging.demo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.testingai.logging.autoconfigure.RequestLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void attachLogAppender() {
		logAppender = new ListAppender<>();
		logAppender.start();
		filterLogger().addAppender(logAppender);
	}

	@AfterEach
	void detachLogAppender() {
		filterLogger().detachAppender(logAppender);
	}

	private Logger filterLogger() {
		return (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
	}

	@Test
	void helloEndpoint_logsRequestLine() throws Exception {
		mockMvc.perform(get("/demo/hello")).andExpect(status().isOk());

		assertThat(formattedMessages()).anyMatch(
				message -> message.contains("GET") && message.contains("/demo/hello") && message.contains("200"));
	}

	@Test
	void echoEndpoint_logsRequestAndResponseBody() throws Exception {
		mockMvc.perform(post("/demo/echo").contentType("application/json").content("{\"message\":\"hi\"}"))
				.andExpect(status().isOk());

		assertThat(formattedMessages()).anyMatch(message -> message.contains("hi"));
	}

	@Test
	void actuatorHealth_isExcludedFromLogging() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		assertThat(formattedMessages()).isEmpty();
	}

	private List<String> formattedMessages() {
		return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd spring-boot-starters && mvn test -pl request-logging/spring-demo -Dtest=RequestLoggingIntegrationTest`
Expected: FAIL — `helloEndpoint_logsRequestLine` and `echoEndpoint_logsRequestAndResponseBody` fail with 404 (`DemoController`/`EchoRequest` don't exist yet, so `status().isOk()` fails). `actuatorHealth_isExcludedFromLogging` already passes at this point — Spring Boot Actuator's `/health` endpoint is exposed by default once the dependency is on the classpath (Step 1), and the filter's own `@DefaultValue` already excludes `/actuator/**` even before Step 6 makes it explicit in `application.yml`.

- [ ] **Step 4: Implement `EchoRequest`**

`spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/EchoRequest.java`:

```java
package com.testingai.logging.demo;

public record EchoRequest(String message) {
}
```

- [ ] **Step 5: Implement `DemoController`**

`spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/DemoController.java`:

```java
package com.testingai.logging.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {

	@GetMapping("/hello")
	public String hello() {
		return "Hello from the request-logging demo!";
	}

	@PostMapping("/echo")
	public EchoRequest echo(@RequestBody EchoRequest request) {
		return request;
	}
}
```

- [ ] **Step 6: Configure the demo's properties**

Replace `spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml` entirely with:

```yaml
server:
  port: 8090

app:
  logging:
    request:
      include-body: true
      excluded-paths:
        - /actuator/**

management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd spring-boot-starters && mvn test -pl request-logging/spring-demo -Dtest=RequestLoggingIntegrationTest`
Expected: PASS, all 3 tests green.

- [ ] **Step 8: Run the full reactor test suite**

Run: `cd spring-boot-starters && mvn clean test`
Expected: `BUILD SUCCESS`, all tests across both modules passing.

- [ ] **Step 9: Commit**

```bash
git add spring-boot-starters/request-logging/spring-demo/pom.xml \
  spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/DemoController.java \
  spring-boot-starters/request-logging/spring-demo/src/main/java/com/testingai/logging/demo/EchoRequest.java \
  spring-boot-starters/request-logging/spring-demo/src/main/resources/application.yml \
  spring-boot-starters/request-logging/spring-demo/src/test/java/com/testingai/logging/demo/RequestLoggingIntegrationTest.java
git commit -m "feat(spring-boot-starters): add DemoController and end-to-end RequestLoggingIntegrationTest"
```

---

### Task 6: READMEs, `CLAUDE.md`, and `.githooks/pre-commit`

**Files:**
- Modify: `spring-boot-starters/README.md`
- Create: `spring-boot-starters/request-logging/spring-demo/README.md`
- Modify: `CLAUDE.md`
- Modify: `.githooks/pre-commit`

- [ ] **Step 1: Finalize the category README**

Replace `spring-boot-starters/README.md` entirely with:

```markdown
# Spring Boot Starters — Demos

This directory contains runnable demonstrations of custom Spring Boot starters — the same
auto-configuration mechanism used by `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
etc., applied to a small feature you might actually want to share across services.

Unlike every other category in this repo, each starter here is **two independent Maven
modules**, not one:

- `<starter>-spring-boot-starter/` — the reusable auto-configuration jar. This is the artifact
  a real project would add as a dependency; it has no knowledge of any demo.
- `spring-demo/` — a runnable Spring Boot app that depends on the starter (via the Maven
  reactor) and exercises it through a couple of REST endpoints, so its behavior is visible
  end-to-end.

| Starter | Demo | What it auto-configures |
|---|---|---|
| [`request-logging`](request-logging/) | `request-logging/spring-demo` | A servlet filter that logs every HTTP request/response (method, path, status, duration), with an on/off switch, opt-in body logging, and path exclusions |

More starters may be added here over time, each following the same two-module shape.
```

- [ ] **Step 2: Write the demo module README**

`spring-boot-starters/request-logging/spring-demo/README.md`:

```markdown
# Request-Logging Spring Boot Starter — Demo

A Spring Boot app that consumes `request-logging-spring-boot-starter` to demonstrate a custom
auto-configured servlet filter that logs every HTTP request and response: method, path, status,
and duration, with three configuration knobs.

## Prerequisites

- Java 21
- Maven 3.9+

All commands below assume your working directory is `spring-boot-starters/` (the reactor
root) — `spring-demo` depends on the starter as a Maven sibling, so it must build inside the
same reactor rather than in isolation.

## Run the app

```bash
mvn spring-boot:run -pl request-logging/spring-demo -am
```

## The starter's properties

| Property | Default | Effect |
|---|---|---|
| `app.logging.request.enabled` | `true` | Master on/off switch for the filter |
| `app.logging.request.include-body` | `false` | Also log (truncated) request/response bodies |
| `app.logging.request.excluded-paths` | `/actuator/**` | Ant-style path patterns to skip entirely |

This demo's `application.yml` turns `include-body` on and keeps the default `excluded-paths`,
so Actuator's own health checks never show up in the log.

## Try it

```bash
# Always-on request-line logging
curl -s http://localhost:8090/demo/hello
# => log line: GET /demo/hello -> 200 (N ms)

# Body logging (include-body: true in this demo's config)
curl -s -X POST http://localhost:8090/demo/echo \
  -H "Content-Type: application/json" \
  -d '{"message":"hello starter"}'
# => log line also includes requestBody={"message":"hello starter"} responseBody={"message":"hello starter"}

# Excluded path — no log line at all
curl -s http://localhost:8090/actuator/health

# Turn the filter off entirely
mvn spring-boot:run -pl request-logging/spring-demo -am -Dspring-boot.run.arguments=--app.logging.request.enabled=false
# repeat the first curl — no log line this time
```

## Swagger UI

http://localhost:8090/swagger-ui/index.html

## Tests

- `request-logging-spring-boot-starter`: `RequestLoggingFilterTest` (filter behavior in
  isolation) and `RequestLoggingAutoConfigurationTest` (conditional wiring, via
  `ApplicationContextRunner`).
- `spring-demo`: `RequestLoggingIntegrationTest` (`MockMvc` + a Logback `ListAppender`, proving
  the auto-configured filter is actually active end-to-end).

Run all of them with `mvn test` from `spring-boot-starters/`.
```

- [ ] **Step 3: Wire `CLAUDE.md`**

Edit `CLAUDE.md` — insert a new command section right after the existing "Saga pattern demo" section (before "### Backend REST API"):

```markdown
### Spring Boot starter demo (run from the reactor root, no docker infrastructure required)

```bash
cd spring-boot-starters

mvn clean package                                            # build the starter and the demo together (reactor build)
mvn test                                                     # unit tests for both modules
mvn test -pl request-logging/spring-demo -Dtest=ClassName    # single test class in the demo
mvn spring-boot:run -pl request-logging/spring-demo -am      # run the demo app (-am builds the starter first)
```

```

Then add a new row to the "Repository layout" table, right after the `distributed-transactions/<pattern>/spring-demo/` row and before the `docker-compose.yml` row:

```markdown
| `spring-boot-starters/<starter>/<starter>-spring-boot-starter/` + `.../spring-demo/` | Custom Spring Boot starter demos — each starter is an auto-configuration jar plus a consuming demo app in the same Maven reactor (currently: request-logging) — no external infrastructure required |
```

- [ ] **Step 4: Wire `.githooks/pre-commit`**

Edit `.githooks/pre-commit` — extend the staged-file grep:

```diff
-STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions)/.*\.java$' || true)
+STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters)/.*\.java$' || true)
```

Add a matching block right after the `distributed-transactions` block (before the "Re-stage" comment):

```bash
if echo "$STAGED_JAVA" | grep -q '^spring-boot-starters/'; then
    echo "[pre-commit] Applying Spotless formatting to staged spring-boot-starters Java files..."
    (cd "$ROOT/spring-boot-starters" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 5: Verify the full build one more time**

Run: `cd spring-boot-starters && mvn clean package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add spring-boot-starters/README.md \
  spring-boot-starters/request-logging/spring-demo/README.md \
  CLAUDE.md \
  .githooks/pre-commit
git commit -m "docs(spring-boot-starters): add module/category READMEs, wire CLAUDE.md and pre-commit hook"
```

---

## Post-implementation

Once all 6 tasks are complete, the repo has a new `spring-boot-starters/` category with a fully tested `request-logging` starter (auto-configuration + demo), matching the conventions of every other category: parent POM, per-module README, Spotless formatting on commit, and a `CLAUDE.md` command section.
