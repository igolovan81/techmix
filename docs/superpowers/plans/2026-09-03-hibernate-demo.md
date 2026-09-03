# Hibernate Data-Access Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `data-access/hibernate/spring-demo` — a Spring Boot app demonstrating Hibernate/JPA-specific mechanics (association fetching and the N+1 problem, first/second-level caching, optimistic/pessimistic locking, and inheritance/embeddable/converter/auditing mapping) over a library lending domain — as a sibling module to `data-access/jooq/spring-demo` inside the `data-access/` reactor.

**Architecture:** One Spring Boot app (`com.testingai.hibernate`), package-per-pattern like every other module in this repo (`entity/`, `repository/`, `associations/`, `caching/`, `locking/`, `inheritance/`, `util/`, `controller/`). A hand-written `schema.sql` is the single source of truth for table DDL (`hibernate.ddl-auto: none`) — Spring Boot bootstraps it against H2 (`MODE=PostgreSQL`) by default (used by `mvn test` and local `spring-boot:run`, no Docker) and against real Postgres under the `postgres` profile.

**Tech Stack:** Spring Boot 3.4.4, Java 21, `spring-boot-starter-data-jpa` (Hibernate 6.6.11.Final, confirmed against this repo's `spring-boot-starter-parent` BOM), `org.hibernate.orm:hibernate-jcache` + `com.github.ben-manes.caffeine:jcache` (both BOM-managed, no explicit version) for L2 region caching, PostgreSQL driver + Postgres 16 (Docker), H2 (default profile), Testcontainers 1.20.6 (one concurrency IT), Lombok, springdoc-openapi, Gatling.

**Spec:** `docs/superpowers/specs/2026-09-03-hibernate-demo-design.md`

## Global Constraints

- Java 21, Spring Boot 3.4.4. **This branch (`feature/hibernate-demo`) was created from `master` before `data-access/jooq/spring-demo`'s implementation was merged** — `master` only has the jOOQ demo's spec/plan *docs*, not its code, so `data-access/pom.xml` does not exist yet here. Task 1 creates it fresh, registering only `hibernate/spring-demo` as a module. **When this branch is eventually merged with the jOOQ implementation branch, `data-access/pom.xml`'s `<modules>` list will need a manual merge to include both `jooq/spring-demo` and `hibernate/spring-demo`** — flag this to the user at that point; do not attempt to pre-resolve it here.
- Module artifactId: `hibernate-demo`; base package: `com.testingai.hibernate`.
- App port: `8105` (next free slot after `data-access/jooq/spring-demo`'s `8104`). Postgres (docker compose): `5435` (jOOQ occupies `5434`).
- `spring.jpa.hibernate.ddl-auto: none` in both profiles — Hibernate maps onto `schema.sql`, it does not generate it. `schema.sql` statements must use `CREATE TABLE IF NOT EXISTS` (idempotent — `spring.sql.init.mode: always` re-runs it on every start, and Postgres keeps data across restarts via the docker compose volume).
- **Deviation from the spec's package sketch:** the spec describes `associations/BookAuthorService` covering all four techniques (naive N+1, `JOIN FETCH`, `@EntityGraph`, `@BatchSize`) as methods on one class. This plan splits it into two services instead: `BookAuthorService` (naive N+1 + `JOIN FETCH` + `@EntityGraph`, all three operating on `Book.authors`, which carries no batch-fetch annotation) and `CopyBatchFetchService` (the `@BatchSize` fix, operating on the separate `LibraryItem.copies` collection). Reason: `@BatchSize` is a blanket, always-on mapping annotation — putting it on `Book.authors` would make the "naive" method no longer demonstrate pure N+1 (Hibernate would silently batch it too), destroying the before/after contrast the pattern exists to show. Splitting the collection keeps the baseline honest.
- Entities are Lombok `@Getter @Setter @NoArgsConstructor` classes (never `record` — JPA entities need mutable state and a no-arg constructor) with `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` and `@EqualsAndHashCode.Include` on `id` only — never `@Data` on an entity with a collection field (recurses into lazy proxies / breaks on unset FK).
- **`LibraryItem` and `Person` are plain `abstract class`, not `sealed`**, despite the modern-Java preference for sealed hierarchies (`.claude/rules/code-review.md`) — verified during implementation, not just anticipated: Hibernate generates a ByteBuddy lazy-load proxy subclass of an entity at `SessionFactory` bootstrap whenever it's the target of a `@ManyToOne`/`@OneToOne` (here, `Copy.libraryItem : LibraryItem`), and the JVM's `sealed`/`permits` check rejects that runtime-generated class, crashing startup with `IncompatibleClassChangeError: ... is not a permitted subclass`. `Book`/`Dvd`/`Magazine`/`Member`/`Librarian` stay `final` — only the abstract roots needed the fix. Task 6's `switch` over these types therefore needs an explicit `default` branch (no `sealed` means no compiler-enforced exhaustiveness).
- Services use constructor injection; `@RequiredArgsConstructor` on `private final` fields is the default, except `AuthorCacheService` (Task 4), which needs an explicit constructor to build a `TransactionTemplate` from the injected `PlatformTransactionManager`.
- `FailureSimulator` follows the exact shape from `message-brokers/kafka/spring-demo/.../util/FailureSimulator.java` per `.claude/rules/code-review.md` (`FAILURE_RATE = 0.05`, `maybeThrow(String context)`, no `shouldFail()`), created in Task 5 where it's first needed (`locking/CopyCheckoutService`) — same placement pattern the jOOQ module used (`FailureSimulator` landed right before the task that needed it).
- `FailureSimulator.maybeThrow` fires unconditionally as the first statement inside both `checkoutOptimistic` and `checkoutPessimistic`, so ~5% of calls throw regardless of request validity. Any test calling either method must go through the `checkoutOptimisticIgnoringSimulatedFailures` / `checkoutPessimisticIgnoringSimulatedFailures` retry helpers defined in Task 5, exactly like jOOQ's `placeOrderIgnoringSimulatedFailures`, or it will be flaky.
- `Statistics` access pattern used throughout `associations/` and `caching/`: `entityManager.unwrap(Session.class).getSessionFactory().getStatistics()`, requires `spring.jpa.properties.hibernate.generate_statistics: true` (set in Task 1's `application.yml`). Call `.clear()` before the measured operation, then read counts after — never subtract before/after (Statistics accumulates for the whole `SessionFactory`, not per-call, so `.clear()` gives an isolated reading).
- Field style matches the rest of the repo: Lombok `@RequiredArgsConstructor` + `private final` on services/injected fields, Java `record` for all DTOs/views/requests, one top-level file per record (matches `data-access/jooq/spring-demo`'s `ProductView`/`CategorySummary` convention — not nested inside the service class, except `DemoController`'s own request DTOs, which stay nested per that module's controller convention).
- If a Hibernate/Spring Data API signature in this plan doesn't match Hibernate 6.6.11.Final or Spring Boot 3.4.4 exactly, consult their docs (via `context7`, libraries `/hibernate/hibernate-orm` and `/spring-projects/spring-data-jpa`) for the current syntax rather than guessing further — the intent (map this schema, demonstrate this specific Hibernate mechanic) is authoritative, not the exact annotation/property spelling shown here.
- Exact whitespace in this plan's code blocks is not load-bearing — `spotless-maven-plugin` (wired via `.githooks/pre-commit`, which already matches the whole `data-access/` prefix — no hook change needed) reformats on commit.

---

## Task 1: Module scaffolding

**Files:**
- Create: `data-access/pom.xml`
- Create: `data-access/eclipse-formatter.xml` (copy of `noSQL/eclipse-formatter.xml`)
- Create: `data-access/hibernate/spring-demo/pom.xml`
- Create: `data-access/hibernate/spring-demo/src/main/resources/db/schema.sql`
- Create: `data-access/hibernate/spring-demo/src/main/resources/application.yml`
- Create: `data-access/hibernate/spring-demo/src/main/resources/application-postgres.yml`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/HibernateDemoApplication.java`
- Create: `data-access/hibernate/docker/docker-compose.yml`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/HibernateDemoApplicationTest.java`

**Interfaces:**
- Produces: a buildable, empty Spring Boot module with the full library-lending schema loaded (`author`, `library_item`, `book_author`, `copy`, `person`, `member`, `librarian`, `loan`) against H2 by default — every later task's entities map onto these tables.

- [ ] **Step 1: Copy the Eclipse formatter config**

```bash
cp noSQL/eclipse-formatter.xml data-access/eclipse-formatter.xml
```

- [ ] **Step 2: Create the `data-access` parent reactor pom**

Create `data-access/pom.xml`:

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
    <artifactId>data-access</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Data Access</name>
    <description>Parent POM for all data-access-layer demo modules</description>

    <modules>
        <module>hibernate/spring-demo</module>
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
                        <exclude>**/*IT.java</exclude>
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

- [ ] **Step 3: Create the module pom**

Create `data-access/hibernate/spring-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>data-access</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>hibernate-demo</artifactId>
    <name>Hibernate Demo</name>
    <description>Learning and demonstration project for Hibernate/JPA association fetching and the N+1 problem, first/second-level caching, optimistic/pessimistic locking, and inheritance/embeddable/converter/auditing mapping</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-jcache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>jcache</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.hibernate.HibernateDemoApplication</mainClass>
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

Note: `h2` and `postgresql` are `runtime` scope (not `test`, unlike jOOQ's `h2`) because this module's default profile runs the app itself against H2 (`mvn spring-boot:run` with no profile) — a `test`-scoped dependency would not be on the classpath for that.

- [ ] **Step 4: Create the schema**

Create `data-access/hibernate/spring-demo/src/main/resources/db/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS author (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS library_item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_type VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    publication_year INT NOT NULL,
    isbn VARCHAR(20),
    region_code VARCHAR(10),
    runtime_minutes INT,
    issue_number VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS book_author (
    book_id BIGINT NOT NULL REFERENCES library_item(id),
    author_id BIGINT NOT NULL REFERENCES author(id),
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE IF NOT EXISTS copy (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    library_item_id BIGINT NOT NULL REFERENCES library_item(id),
    barcode VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS person (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS member (
    person_id BIGINT PRIMARY KEY REFERENCES person(id),
    membership_date DATE NOT NULL,
    street VARCHAR(200),
    city VARCHAR(100),
    zip_code VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS librarian (
    person_id BIGINT PRIMARY KEY REFERENCES person(id),
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    department VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS loan (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    copy_id BIGINT NOT NULL REFERENCES copy(id),
    member_id BIGINT NOT NULL REFERENCES member(person_id),
    loan_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

- [ ] **Step 5: Create `application.yml`**

Create `data-access/hibernate/spring-demo/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:hibernatedemo;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        generate_statistics: true
        cache:
          use_second_level_cache: true
          region:
            factory_class: jcache
        javax:
          cache:
            provider: com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider

server:
  port: 8105

springdoc:
  swagger-ui:
    path: /swagger-ui/index.html
```

- [ ] **Step 6: Create `application-postgres.yml`**

Create `data-access/hibernate/spring-demo/src/main/resources/application-postgres.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/hibernatedemo
    username: hibernate
    password: hibernate
    driver-class-name: org.postgresql.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql
```

- [ ] **Step 7: Create the docker compose file**

Create `data-access/hibernate/docker/docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: hibernate-postgres
    environment:
      POSTGRES_DB: hibernatedemo
      POSTGRES_USER: hibernate
      POSTGRES_PASSWORD: hibernate
    ports:
      - "5435:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U hibernate -d hibernatedemo"]
      interval: 5s
      timeout: 5s
      retries: 10
```

- [ ] **Step 8: Create the application main class**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/HibernateDemoApplication.java`:

```java
package com.testingai.hibernate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HibernateDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(HibernateDemoApplication.class, args);
	}
}
```

(`@EnableJpaAuditing` is added here, in Task 1, rather than in Task 2 where `Loan`'s `@CreatedDate`/`@LastModifiedDate` are introduced, because it's application-wide bootstrap configuration, not part of the entity mapping itself.)

- [ ] **Step 9: Write the smoke test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/HibernateDemoApplicationTest.java`:

```java
package com.testingai.hibernate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HibernateDemoApplicationTest {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 10: Build and verify**

Run: `cd data-access && mvn clean package`
Expected: BUILD SUCCESS — the app context loads against H2 with the schema above, no Docker needed.

- [ ] **Step 11: Commit**

```bash
git add data-access/pom.xml data-access/eclipse-formatter.xml data-access/hibernate
git commit -m "feat(hibernate): scaffold module with library-lending schema

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 2: `entity/` and `repository/` — domain model

**Files:**
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Address.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Isbn.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/IsbnConverter.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/CopyStatus.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/LibraryItem.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Book.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Dvd.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Magazine.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Author.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Copy.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Person.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Member.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Librarian.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Loan.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/BookRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/AuthorRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/CopyRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/LibraryItemRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/PersonRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/MemberRepository.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/LoanRepository.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/entity/EntityMappingTest.java`

**Interfaces:**
- Produces: `LibraryItem`/`Book`/`Dvd`/`Magazine` (SINGLE_TABLE), `Person`/`Member`/`Librarian` (JOINED), `Author`, `Copy` (with `@Version`), `Loan` (with `@CreatedDate`/`@LastModifiedDate`), `Address` (`@Embeddable`), `Isbn`+`IsbnConverter`, `CopyStatus` enum; `BookRepository`, `AuthorRepository`, `CopyRepository`, `LibraryItemRepository`, `PersonRepository`, `MemberRepository`, `LoanRepository` — every later task depends on these exact type/field names.

- [ ] **Step 1: Write the failing entity mapping test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/entity/EntityMappingTest.java`:

```java
package com.testingai.hibernate.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.LoanRepository;
import com.testingai.hibernate.repository.MemberRepository;
import com.testingai.hibernate.repository.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class EntityMappingTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private LoanRepository loanRepository;

	@Test
	void singleTableInheritancePersistsAndReloadsCorrectSubtypes() {
		Author author = new Author();
		author.setName("Martin Fowler");
		authorRepository.save(author);

		Book book = new Book();
		book.setTitle("Refactoring");
		book.setPublicationYear(1999);
		book.setIsbn(new Isbn("978-0134757599"));
		book.setAuthors(Set.of(author));
		bookRepository.save(book);

		Dvd dvd = new Dvd();
		dvd.setTitle("The Matrix");
		dvd.setPublicationYear(1999);
		dvd.setRegionCode("A");
		dvd.setRuntimeMinutes(136);
		libraryItemRepository.save(dvd);

		entityManager.flush();
		entityManager.clear();

		List<LibraryItem> items = libraryItemRepository.findAll();
		assertThat(items).hasSize(2);
		assertThat(items).anyMatch(Book.class::isInstance).anyMatch(Dvd.class::isInstance);

		Book reloadedBook = bookRepository.findAll().get(0);
		assertThat(reloadedBook.getIsbn()).isEqualTo(new Isbn("978-0134757599"));
		assertThat(reloadedBook.getAuthors()).extracting(Author::getName).containsExactly("Martin Fowler");
	}

	@Test
	void joinedInheritancePersistsAndReloadsCorrectSubtypes() {
		Member member = new Member();
		member.setName("Ada Lovelace");
		member.setEmail("ada@example.com");
		member.setMembershipDate(LocalDate.of(2020, 1, 1));
		member.setAddress(new Address("1 Analytical Engine Way", "London", "SW1A 1AA"));
		memberRepository.save(member);

		Librarian librarian = new Librarian();
		librarian.setName("Marian the Librarian");
		librarian.setEmail("marian@example.com");
		librarian.setEmployeeId("EMP-001");
		librarian.setDepartment("Reference");
		personRepository.save(librarian);

		entityManager.flush();
		entityManager.clear();

		List<Person> people = personRepository.findAll();
		assertThat(people).hasSize(2);
		assertThat(people).anyMatch(Member.class::isInstance).anyMatch(Librarian.class::isInstance);

		Member reloadedMember = memberRepository.findAll().get(0);
		assertThat(reloadedMember.getAddress().getCity()).isEqualTo("London");
	}

	@Test
	void loanAuditingStampsCreatedAndUpdatedTimestampsOnSave() {
		Member member = new Member();
		member.setName("Grace Hopper");
		member.setEmail("grace@example.com");
		member.setMembershipDate(LocalDate.of(2021, 1, 1));
		memberRepository.save(member);

		Book book = new Book();
		book.setTitle("The Pragmatic Programmer");
		book.setPublicationYear(1999);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		entityManager.persist(copy);

		Loan loan = new Loan();
		loan.setCopy(copy);
		loan.setMember(member);
		loan.setLoanDate(LocalDate.now());
		loan.setDueDate(LocalDate.now().plusWeeks(2));
		loanRepository.save(loan);

		entityManager.flush();
		entityManager.clear();

		Loan reloaded = loanRepository.findAll().get(0);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=EntityMappingTest`
Expected: FAIL — none of the entity/repository classes exist yet (compile error).

- [ ] **Step 3: Create the value types**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Isbn.java`:

```java
package com.testingai.hibernate.entity;

public record Isbn(String value) {

	public Isbn {
		if (value == null || !value.matches("\\d{3}-\\d{10}")) {
			throw new IllegalArgumentException("ISBN must match ddd-dddddddddd: " + value);
		}
	}
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/IsbnConverter.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class IsbnConverter implements AttributeConverter<Isbn, String> {

	@Override
	public String convertToDatabaseColumn(Isbn isbn) {
		return isbn == null ? null : isbn.value();
	}

	@Override
	public Isbn convertToEntityAttribute(String dbData) {
		return dbData == null ? null : new Isbn(dbData);
	}
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Address.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Address {

	@Column(name = "street")
	private String street;

	@Column(name = "city")
	private String city;

	@Column(name = "zip_code")
	private String zipCode;
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/CopyStatus.java`:

```java
package com.testingai.hibernate.entity;

public enum CopyStatus {
	AVAILABLE,
	ON_LOAN,
	LOST
}
```

- [ ] **Step 4: Create the `LibraryItem` SINGLE_TABLE hierarchy**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/LibraryItem.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "library_item")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "item_type")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// Not sealed: Hibernate generates a ByteBuddy lazy-load proxy subclass of LibraryItem at runtime
// (Copy.libraryItem is a lazy @ManyToOne to it) and the JVM's sealed/permits check rejects that
// generated class, crashing SessionFactory bootstrap with IncompatibleClassChangeError.
public abstract class LibraryItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "publication_year", nullable = false)
	private int publicationYear;

	@OneToMany(mappedBy = "libraryItem", fetch = FetchType.LAZY)
	@BatchSize(size = 10)
	private List<Copy> copies = new ArrayList<>();
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Book.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("BOOK")
@Getter
@Setter
@NoArgsConstructor
public final class Book extends LibraryItem {

	@Convert(converter = IsbnConverter.class)
	private Isbn isbn;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			name = "book_author",
			joinColumns = @JoinColumn(name = "book_id"),
			inverseJoinColumns = @JoinColumn(name = "author_id"))
	private Set<Author> authors = new HashSet<>();
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Dvd.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("DVD")
@Getter
@Setter
@NoArgsConstructor
public final class Dvd extends LibraryItem {

	@Column(name = "region_code")
	private String regionCode;

	@Column(name = "runtime_minutes")
	private Integer runtimeMinutes;
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Magazine.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("MAGAZINE")
@Getter
@Setter
@NoArgsConstructor
public final class Magazine extends LibraryItem {

	@Column(name = "issue_number")
	private String issueNumber;
}
```

- [ ] **Step 5: Create `Author` and `Copy`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Author.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "author")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Author {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Copy.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "copy")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Copy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "library_item_id", nullable = false)
	private LibraryItem libraryItem;

	@Column(name = "barcode", nullable = false, unique = true)
	private String barcode;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private CopyStatus status = CopyStatus.AVAILABLE;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;
}
```

- [ ] **Step 6: Create the `Person` JOINED hierarchy**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Person.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "person")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "person_type")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// Not sealed: same reason as LibraryItem (see there) — Hibernate builds a lazy-load proxy factory
// for every entity persister, including abstract inheritance roots, regardless of whether a
// specific association happens to be lazy; sealed/permits rejects the generated proxy subclass.
public abstract class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "email", nullable = false, unique = true)
	private String email;
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Member.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "member")
@DiscriminatorValue("MEMBER")
@PrimaryKeyJoinColumn(name = "person_id")
@Getter
@Setter
@NoArgsConstructor
public final class Member extends Person {

	@Column(name = "membership_date", nullable = false)
	private LocalDate membershipDate;

	@Embedded
	private Address address;
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Librarian.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "librarian")
@DiscriminatorValue("LIBRARIAN")
@PrimaryKeyJoinColumn(name = "person_id")
@Getter
@Setter
@NoArgsConstructor
public final class Librarian extends Person {

	@Column(name = "employee_id", nullable = false, unique = true)
	private String employeeId;

	@Column(name = "department", nullable = false)
	private String department;
}
```

- [ ] **Step 7: Create `Loan`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Loan.java`:

```java
package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "loan")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Loan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "copy_id", nullable = false)
	private Copy copy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "loan_date", nullable = false)
	private LocalDate loanDate;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "return_date")
	private LocalDate returnDate;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
```

- [ ] **Step 8: Create the repositories**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/BookRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Book;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

	@Query("select distinct b from Book b left join fetch b.authors")
	List<Book> findAllWithAuthorsJoinFetch();

	@EntityGraph(attributePaths = "authors")
	@Query("select b from Book b")
	List<Book> findAllWithAuthorsEntityGraph();
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/AuthorRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/CopyRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Copy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyRepository extends JpaRepository<Copy, Long> {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/LibraryItemRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.LibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/PersonRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/MemberRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository/LoanRepository.java`:

```java
package com.testingai.hibernate.repository;

import com.testingai.hibernate.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `mvn test -Dtest=EntityMappingTest`
Expected: PASS (3 tests)

- [ ] **Step 10: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/repository data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/entity
git commit -m "feat(hibernate): add entity/ domain model and repository/ interfaces

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 3: `associations/` — N+1, `JOIN FETCH`, `@EntityGraph`, `@BatchSize`

**Files:**
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookWithAuthorNames.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookFetchResult.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookAuthorService.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/ItemWithCopyCount.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/ItemFetchResult.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/CopyBatchFetchService.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/associations/BookAuthorServiceTest.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/associations/CopyBatchFetchServiceTest.java`

**Interfaces:**
- Consumes: `BookRepository`, `LibraryItemRepository`, `AuthorRepository`, `Book`, `Author`, `LibraryItem`, `Copy` (Task 2).
- Produces: `BookAuthorService.demonstrateNPlusOne()`, `.findAllWithJoinFetch()`, `.findAllWithEntityGraph()` (each returns `BookFetchResult(List<BookWithAuthorNames> books, long sqlStatementCount)`); `CopyBatchFetchService.demonstrateBatchFetch()` (returns `ItemFetchResult(List<ItemWithCopyCount> items, long sqlStatementCount)`) — used by `controller/DemoController` (Task 7).

- [ ] **Step 1: Write the failing test for `BookAuthorService`**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/associations/BookAuthorServiceTest.java`:

```java
package com.testingai.hibernate.associations;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(BookAuthorService.class)
class BookAuthorServiceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookAuthorService bookAuthorService;

	@BeforeEach
	void seedThreeBooksEachWithOneAuthor() {
		for (int i = 0; i < 3; i++) {
			Author author = new Author();
			author.setName("Author " + i);
			authorRepository.save(author);

			Book book = new Book();
			book.setTitle("Book " + i);
			book.setPublicationYear(2000 + i);
			book.setAuthors(Set.of(author));
			bookRepository.save(book);
		}
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void naiveFetchExecutesOneSelectPerBookForAuthors() {
		BookFetchResult result = bookAuthorService.demonstrateNPlusOne();

		assertThat(result.books()).hasSize(3);
		// 1 select for findAll() + 3 selects, one per book, to lazily load its authors collection
		assertThat(result.sqlStatementCount()).isEqualTo(4);
	}

	@Test
	void joinFetchExecutesExactlyOneSelect() {
		BookFetchResult result = bookAuthorService.findAllWithJoinFetch();

		assertThat(result.books()).hasSize(3);
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}

	@Test
	void entityGraphExecutesExactlyOneSelect() {
		BookFetchResult result = bookAuthorService.findAllWithEntityGraph();

		assertThat(result.books()).hasSize(3);
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=BookAuthorServiceTest`
Expected: FAIL — `BookAuthorService`, `BookFetchResult`, `BookWithAuthorNames` do not exist yet.

- [ ] **Step 3: Implement `BookAuthorService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookWithAuthorNames.java`:

```java
package com.testingai.hibernate.associations;

import java.util.List;

public record BookWithAuthorNames(Long id, String title, List<String> authorNames) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookFetchResult.java`:

```java
package com.testingai.hibernate.associations;

import java.util.List;

public record BookFetchResult(List<BookWithAuthorNames> books, long sqlStatementCount) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/BookAuthorService.java`:

```java
package com.testingai.hibernate.associations;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.repository.BookRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookAuthorService {

	private final BookRepository bookRepository;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public BookFetchResult demonstrateNPlusOne() {
		statistics().clear();
		List<Book> books = bookRepository.findAll();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	@Transactional(readOnly = true)
	public BookFetchResult findAllWithJoinFetch() {
		statistics().clear();
		List<Book> books = bookRepository.findAllWithAuthorsJoinFetch();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	@Transactional(readOnly = true)
	public BookFetchResult findAllWithEntityGraph() {
		statistics().clear();
		List<Book> books = bookRepository.findAllWithAuthorsEntityGraph();
		return new BookFetchResult(toViews(books), statistics().getPrepareStatementCount());
	}

	private List<BookWithAuthorNames> toViews(List<Book> books) {
		return books.stream()
				.map(book -> new BookWithAuthorNames(
						book.getId(),
						book.getTitle(),
						book.getAuthors().stream().map(Author::getName).toList()))
				.toList();
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=BookAuthorServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Write the failing test for `CopyBatchFetchService`**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/associations/CopyBatchFetchServiceTest.java`:

```java
package com.testingai.hibernate.associations;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CopyBatchFetchService.class)
class CopyBatchFetchServiceTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private CopyBatchFetchService copyBatchFetchService;

	@BeforeEach
	void seedFifteenItemsEachWithOneCopy() {
		for (int i = 0; i < 15; i++) {
			Book book = new Book();
			book.setTitle("Book " + i);
			book.setPublicationYear(2000 + i);
			libraryItemRepository.save(book);

			Copy copy = new Copy();
			copy.setLibraryItem(book);
			copy.setBarcode("BC-" + i);
			copy.setStatus(CopyStatus.AVAILABLE);
			copyRepository.save(copy);
		}
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	void batchFetchGroupsCollectionLoadsIntoBatchesOfTen() {
		ItemFetchResult result = copyBatchFetchService.demonstrateBatchFetch();

		assertThat(result.items()).hasSize(15);
		assertThat(result.items()).allMatch(item -> item.copyCount() == 1);
		// 1 select for findAll() + 2 batched selects for 15 items' copies (batch size 10: one batch of 10, one of 5)
		assertThat(result.sqlStatementCount()).isEqualTo(3);
	}
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn test -Dtest=CopyBatchFetchServiceTest`
Expected: FAIL — `CopyBatchFetchService`, `ItemFetchResult`, `ItemWithCopyCount` do not exist yet.

- [ ] **Step 7: Implement `CopyBatchFetchService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/ItemWithCopyCount.java`:

```java
package com.testingai.hibernate.associations;

public record ItemWithCopyCount(Long id, String title, int copyCount) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/ItemFetchResult.java`:

```java
package com.testingai.hibernate.associations;

import java.util.List;

public record ItemFetchResult(List<ItemWithCopyCount> items, long sqlStatementCount) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations/CopyBatchFetchService.java`:

```java
package com.testingai.hibernate.associations;

import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.repository.LibraryItemRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyBatchFetchService {

	private final LibraryItemRepository libraryItemRepository;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public ItemFetchResult demonstrateBatchFetch() {
		statistics().clear();
		List<LibraryItem> items = libraryItemRepository.findAll();
		List<ItemWithCopyCount> views = items.stream()
				.map(item -> new ItemWithCopyCount(item.getId(), item.getTitle(), item.getCopies().size()))
				.toList();
		return new ItemFetchResult(views, statistics().getPrepareStatementCount());
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn test -Dtest=CopyBatchFetchServiceTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/associations data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/associations
git commit -m "feat(hibernate): add associations/ N+1, JOIN FETCH, @EntityGraph, @BatchSize demo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 4: `caching/` — first- and second-level cache

**Files:**
- Modify: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Author.java` (add `@Cacheable` + `@Cache`)
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/L1Result.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/L2Result.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/AuthorCacheService.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/caching/AuthorCacheServiceTest.java`

**Interfaces:**
- Consumes: `AuthorRepository`, `Author` (Task 2).
- Produces: `AuthorCacheService.readTwiceInSameTransaction(Long authorId)` returning `L1Result(String firstReadName, String secondReadName, long sqlStatementCount)`; `.readAcrossTwoTransactions(Long authorId)` returning `L2Result(long missesAfterFirstRead, long hitsAfterFirstRead, long missesAfterSecondRead, long hitsAfterSecondRead)` — used by `controller/DemoController` (Task 7).

- [ ] **Step 1: Write the failing test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/caching/AuthorCacheServiceTest.java`:

```java
package com.testingai.hibernate.caching;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.repository.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(AuthorCacheService.class)
// @DataJpaTest wraps each test method in a transaction that rolls back at the end; NOT_SUPPORTED
// disables that here so @BeforeEach's save() actually commits, making the seeded Author visible to
// AuthorCacheService's own REQUIRES_NEW transactions (a separate physical transaction can't see
// another transaction's uncommitted rows).
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorCacheServiceTest {

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private AuthorCacheService authorCacheService;

	private Long authorId;

	@BeforeEach
	void seedOneAuthor() {
		Author author = new Author();
		author.setName("Isaac Asimov");
		authorId = authorRepository.save(author).getId();
	}

	@Test
	void secondReadInSameTransactionHitsFirstLevelCacheNotTheDatabase() {
		L1Result result = authorCacheService.readTwiceInSameTransaction(authorId);

		assertThat(result.firstReadName()).isEqualTo("Isaac Asimov");
		assertThat(result.secondReadName()).isEqualTo("Isaac Asimov");
		// exactly one SELECT for both reads — the second is served from the persistence context (L1)
		assertThat(result.sqlStatementCount()).isEqualTo(1);
	}

	@Test
	void secondReadInANewTransactionHitsSecondLevelCache() {
		L2Result result = authorCacheService.readAcrossTwoTransactions(authorId);

		assertThat(result.missesAfterFirstRead()).isEqualTo(1);
		assertThat(result.hitsAfterFirstRead()).isEqualTo(0);
		assertThat(result.missesAfterSecondRead()).isEqualTo(1);
		assertThat(result.hitsAfterSecondRead()).isEqualTo(1);
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=AuthorCacheServiceTest`
Expected: FAIL — `AuthorCacheService`, `L1Result`, `L2Result` do not exist yet.

- [ ] **Step 3: Enable L2 caching on `Author`**

Modify `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Author.java`, adding two imports and two annotations on the class:

```java
import jakarta.persistence.Cacheable;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
```

```java
@Entity
@Table(name = "author")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "author")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Author {
	// ... unchanged
}
```

- [ ] **Step 4: Implement `AuthorCacheService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/L1Result.java`:

```java
package com.testingai.hibernate.caching;

public record L1Result(String firstReadName, String secondReadName, long sqlStatementCount) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/L2Result.java`:

```java
package com.testingai.hibernate.caching;

public record L2Result(
		long missesAfterFirstRead, long hitsAfterFirstRead, long missesAfterSecondRead, long hitsAfterSecondRead) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching/AuthorCacheService.java`:

```java
package com.testingai.hibernate.caching;

import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthorCacheService {

	private final AuthorRepository authorRepository;
	private final EntityManager entityManager;
	private final TransactionTemplate transactionTemplate;

	public AuthorCacheService(
			AuthorRepository authorRepository,
			EntityManager entityManager,
			PlatformTransactionManager transactionManager) {
		this.authorRepository = authorRepository;
		this.entityManager = entityManager;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		// REQUIRES_NEW: each call must open a genuinely separate physical transaction/session so
		// readAcrossTwoTransactions demonstrates cross-session L2 behavior rather than just reusing
		// whatever transaction (e.g. a @DataJpaTest test transaction) happens to already be open.
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public L1Result readTwiceInSameTransaction(Long authorId) {
		statistics().clear();
		return transactionTemplate.execute(status -> {
			Author first = authorRepository.findById(authorId).orElseThrow();
			Author second = authorRepository.findById(authorId).orElseThrow();
			return new L1Result(first.getName(), second.getName(), statistics().getPrepareStatementCount());
		});
	}

	public L2Result readAcrossTwoTransactions(Long authorId) {
		statistics().clear();
		transactionTemplate.executeWithoutResult(status -> authorRepository.findById(authorId).orElseThrow());
		long missesAfterFirstRead = statistics().getSecondLevelCacheMissCount();
		long hitsAfterFirstRead = statistics().getSecondLevelCacheHitCount();

		transactionTemplate.executeWithoutResult(status -> authorRepository.findById(authorId).orElseThrow());
		long missesAfterSecondRead = statistics().getSecondLevelCacheMissCount();
		long hitsAfterSecondRead = statistics().getSecondLevelCacheHitCount();

		return new L2Result(missesAfterFirstRead, hitsAfterFirstRead, missesAfterSecondRead, hitsAfterSecondRead);
	}

	private Statistics statistics() {
		return entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
	}
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=AuthorCacheServiceTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/entity/Author.java data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/caching data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/caching
git commit -m "feat(hibernate): add caching/ L1 vs L2 cache demo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 5: `util/FailureSimulator` and `locking/` — optimistic vs. pessimistic checkout

**Files:**
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/util/FailureSimulator.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/util/FailureSimulatorTest.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/locking/CopyAlreadyLoanedException.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/locking/CopyCheckoutService.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/locking/CopyCheckoutServiceTest.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/locking/CopyCheckoutServiceConcurrencyIT.java`

**Interfaces:**
- Consumes: `CopyRepository`, `MemberRepository`, `LoanRepository`, `Copy`, `CopyStatus`, `Member`, `Loan` (Task 2).
- Produces: `FailureSimulator.maybeThrow(String context)`; `CopyCheckoutService.checkoutOptimistic(Long copyId, Long memberId)` and `.checkoutPessimistic(Long copyId, Long memberId)`, both returning `Loan` and throwing `CopyAlreadyLoanedException` — used by `controller/DemoController` (Task 7).

- [ ] **Step 1: Write the failing `FailureSimulator` test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/util/FailureSimulatorTest.java`:

```java
package com.testingai.hibernate.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureSimulatorTest {

	@Test
	void maybeThrowDoesNotThrowMostOfTheTime() {
		int failures = 0;
		for (int i = 0; i < 1000; i++) {
			try {
				FailureSimulator.maybeThrow("test");
			} catch (RuntimeException ignored) {
				failures++;
			}
		}

		// With a 5% failure rate, expect roughly 50 failures across 1000 trials; accept a 5-200 range,
		// matching message-brokers/kafka's FailureSimulatorTest.groovy convention.
		assertThat(failures).isBetween(5, 200);
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=FailureSimulatorTest`
Expected: FAIL — `FailureSimulator` does not exist yet.

- [ ] **Step 3: Implement `FailureSimulator`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/util/FailureSimulator.java`:

```java
package com.testingai.hibernate.util;

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
Expected: PASS

- [ ] **Step 5: Write the failing `CopyCheckoutService` test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/locking/CopyCheckoutServiceTest.java`:

```java
package com.testingai.hibernate.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CopyCheckoutService.class)
class CopyCheckoutServiceTest {

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CopyCheckoutService copyCheckoutService;

	private Long availableCopyId;
	private Long memberId;

	@BeforeEach
	void seedOneAvailableCopyAndOneMember() {
		Book book = new Book();
		book.setTitle("Domain-Driven Design");
		book.setPublicationYear(2003);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-LOCK-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		availableCopyId = copyRepository.save(copy).getId();

		Member member = new Member();
		member.setName("Eric Evans");
		member.setEmail("eric@example.com");
		member.setMembershipDate(LocalDate.now());
		memberId = memberRepository.save(member).getId();
	}

	@Test
	void checkoutOptimisticMarksCopyOnLoanAndCreatesLoan() {
		Loan loan = ignoringSimulatedFailures(
				() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId));

		assertThat(loan.getId()).isNotNull();
		assertThat(copyRepository.findById(availableCopyId).orElseThrow().getStatus())
				.isEqualTo(CopyStatus.ON_LOAN);
	}

	@Test
	void checkoutOptimisticOnAnAlreadyLoanedCopyThrows() {
		ignoringSimulatedFailures(() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId));

		assertThatThrownBy(() -> ignoringSimulatedFailures(
						() -> copyCheckoutService.checkoutOptimistic(availableCopyId, memberId)))
				.isInstanceOf(CopyAlreadyLoanedException.class);
	}

	@Test
	void checkoutPessimisticMarksCopyOnLoanAndCreatesLoan() {
		Loan loan = ignoringSimulatedFailures(
				() -> copyCheckoutService.checkoutPessimistic(availableCopyId, memberId));

		assertThat(loan.getId()).isNotNull();
		assertThat(copyRepository.findById(availableCopyId).orElseThrow().getStatus())
				.isEqualTo(CopyStatus.ON_LOAN);
	}

	private <T> T ignoringSimulatedFailures(Supplier<T> call) {
		while (true) {
			try {
				return call.get();
			} catch (CopyAlreadyLoanedException e) {
				throw e;
			} catch (RuntimeException simulatedFailure) {
				// FailureSimulator fires unconditionally as the first statement in both checkout methods;
				// retry until we get past it, but never swallow the real assertion target.
			}
		}
	}
}
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn test -Dtest=CopyCheckoutServiceTest`
Expected: FAIL — `CopyCheckoutService`, `CopyAlreadyLoanedException` do not exist yet.

- [ ] **Step 7: Implement `CopyCheckoutService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/locking/CopyAlreadyLoanedException.java`:

```java
package com.testingai.hibernate.locking;

public class CopyAlreadyLoanedException extends RuntimeException {

	public CopyAlreadyLoanedException(Long copyId) {
		super("Copy already on loan: " + copyId);
	}
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/locking/CopyCheckoutService.java`:

```java
package com.testingai.hibernate.locking;

import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LoanRepository;
import com.testingai.hibernate.repository.MemberRepository;
import com.testingai.hibernate.util.FailureSimulator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyCheckoutService {

	private final CopyRepository copyRepository;
	private final MemberRepository memberRepository;
	private final LoanRepository loanRepository;
	private final EntityManager entityManager;

	@Transactional
	public Loan checkoutOptimistic(Long copyId, Long memberId) {
		FailureSimulator.maybeThrow("CopyCheckoutService.checkoutOptimistic");

		Copy copy = copyRepository.findById(copyId)
				.orElseThrow(() -> new NoSuchElementException("Copy not found: " + copyId));
		if (copy.getStatus() != CopyStatus.AVAILABLE) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		copy.setStatus(CopyStatus.ON_LOAN);
		try {
			// saveAndFlush forces the @Version check to run now, inside this method, rather than
			// deferring it to transaction commit — so a conflict surfaces here, not later.
			copyRepository.saveAndFlush(copy);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		return createLoan(copy, memberId);
	}

	@Transactional
	public Loan checkoutPessimistic(Long copyId, Long memberId) {
		FailureSimulator.maybeThrow("CopyCheckoutService.checkoutPessimistic");

		Copy copy = entityManager.find(Copy.class, copyId, LockModeType.PESSIMISTIC_WRITE);
		if (copy == null) {
			throw new NoSuchElementException("Copy not found: " + copyId);
		}
		if (copy.getStatus() != CopyStatus.AVAILABLE) {
			throw new CopyAlreadyLoanedException(copyId);
		}
		copy.setStatus(CopyStatus.ON_LOAN);
		return createLoan(copy, memberId);
	}

	private Loan createLoan(Copy copy, Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new NoSuchElementException("Member not found: " + memberId));

		Loan loan = new Loan();
		loan.setCopy(copy);
		loan.setMember(member);
		loan.setLoanDate(LocalDate.now());
		loan.setDueDate(LocalDate.now().plusWeeks(2));
		return loanRepository.save(loan);
	}
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn test -Dtest=CopyCheckoutServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 9: Write the concurrency IT (Testcontainers Postgres — the one exception excluded from `mvn test`)**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/locking/CopyCheckoutServiceConcurrencyIT.java`:

```java
package com.testingai.hibernate.locking;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("postgres")
class CopyCheckoutServiceConcurrencyIT {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("hibernatedemo")
			.withUsername("hibernate")
			.withPassword("hibernate");

	@DynamicPropertySource
	static void overrideDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private CopyRepository copyRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private CopyCheckoutService copyCheckoutService;

	private Long copyId;
	private Long firstMemberId;
	private Long secondMemberId;

	@BeforeEach
	void seedOneAvailableCopyAndTwoMembers() {
		Book book = new Book();
		book.setTitle("Concurrent checkout target");
		book.setPublicationYear(2020);
		libraryItemRepository.save(book);

		Copy copy = new Copy();
		copy.setLibraryItem(book);
		copy.setBarcode("BC-CONCURRENT-1");
		copy.setStatus(CopyStatus.AVAILABLE);
		copyId = copyRepository.save(copy).getId();

		Member first = new Member();
		first.setName("First Caller");
		first.setEmail("first@example.com");
		first.setMembershipDate(LocalDate.now());
		firstMemberId = memberRepository.save(first).getId();

		Member second = new Member();
		second.setName("Second Caller");
		second.setEmail("second@example.com");
		second.setMembershipDate(LocalDate.now());
		secondMemberId = memberRepository.save(second).getId();
	}

	@Test
	void pessimisticLockingSerializesConcurrentCheckoutsOfTheSameCopy() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Boolean> firstCheckout = () -> checkoutIgnoringSimulatedFailures(firstMemberId);
			Callable<Boolean> secondCheckout = () -> checkoutIgnoringSimulatedFailures(secondMemberId);

			List<Future<Boolean>> results = executor.invokeAll(List.of(firstCheckout, secondCheckout));
			boolean firstSucceeded = results.get(0).get();
			boolean secondSucceeded = results.get(1).get();

			// exactly one of the two concurrent callers succeeds; the other's post-lock read finds
			// the copy already ON_LOAN and throws CopyAlreadyLoanedException — no double-checkout.
			assertThat(firstSucceeded ^ secondSucceeded).isTrue();
		} finally {
			executor.shutdown();
		}
	}

	private boolean checkoutIgnoringSimulatedFailures(Long memberId) {
		while (true) {
			try {
				Loan loan = copyCheckoutService.checkoutPessimistic(copyId, memberId);
				return loan.getId() != null;
			} catch (CopyAlreadyLoanedException e) {
				return false;
			} catch (RuntimeException simulatedFailure) {
				// retry past FailureSimulator's random 5% failure
			}
		}
	}
}
```

- [ ] **Step 10: Run the IT (requires a Docker daemon)**

Run: `mvn test -Dtest=CopyCheckoutServiceConcurrencyIT`
Expected: PASS — Testcontainers starts a real Postgres instance; the two concurrent `checkoutPessimistic` calls serialize on the row lock and exactly one succeeds.

- [ ] **Step 11: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/util data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/locking data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/util data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/locking
git commit -m "feat(hibernate): add locking/ optimistic vs pessimistic checkout demo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 6: `inheritance/` — polymorphic queries over both hierarchies

**Files:**
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/LibraryItemSummary.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/LibraryCatalogService.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/PersonSummary.java`
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/PersonDirectoryService.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/inheritance/LibraryCatalogServiceTest.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/inheritance/PersonDirectoryServiceTest.java`

**Interfaces:**
- Consumes: `LibraryItemRepository`, `PersonRepository`, `LibraryItem`/`Book`/`Dvd`/`Magazine`, `Person`/`Member`/`Librarian` (Task 2).
- Produces: `LibraryCatalogService.listAll()`, `.listBooks()`, `.listDvds()`, `.listMagazines()` (each returning `List<LibraryItemSummary>`); `PersonDirectoryService.listAllPeople()` (returning `List<PersonSummary>`) — used by `controller/DemoController` (Task 7).

- [ ] **Step 1: Write the failing tests**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/inheritance/LibraryCatalogServiceTest.java`:

```java
package com.testingai.hibernate.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Dvd;
import com.testingai.hibernate.entity.Magazine;
import com.testingai.hibernate.repository.LibraryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(LibraryCatalogService.class)
class LibraryCatalogServiceTest {

	@Autowired
	private LibraryItemRepository libraryItemRepository;

	@Autowired
	private LibraryCatalogService libraryCatalogService;

	@BeforeEach
	void seedOneOfEachSubtype() {
		Book book = new Book();
		book.setTitle("Clean Code");
		book.setPublicationYear(2008);
		libraryItemRepository.save(book);

		Dvd dvd = new Dvd();
		dvd.setTitle("The Matrix");
		dvd.setPublicationYear(1999);
		dvd.setRegionCode("A");
		dvd.setRuntimeMinutes(136);
		libraryItemRepository.save(dvd);

		Magazine magazine = new Magazine();
		magazine.setTitle("National Geographic");
		magazine.setPublicationYear(2024);
		magazine.setIssueNumber("2024-06");
		libraryItemRepository.save(magazine);
	}

	@Test
	void listAllReturnsAllThreeSubtypesPolymorphically() {
		assertThat(libraryCatalogService.listAll()).hasSize(3).extracting(LibraryItemSummary::itemType)
				.containsExactlyInAnyOrder("BOOK", "DVD", "MAGAZINE");
	}

	@Test
	void listBooksReturnsOnlyBooks() {
		assertThat(libraryCatalogService.listBooks()).hasSize(1)
				.allMatch(item -> item.itemType().equals("BOOK"));
	}

	@Test
	void listDvdsReturnsOnlyDvds() {
		assertThat(libraryCatalogService.listDvds()).hasSize(1)
				.allMatch(item -> item.itemType().equals("DVD"));
	}

	@Test
	void listMagazinesReturnsOnlyMagazines() {
		assertThat(libraryCatalogService.listMagazines()).hasSize(1)
				.allMatch(item -> item.itemType().equals("MAGAZINE"));
	}
}
```

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/inheritance/PersonDirectoryServiceTest.java`:

```java
package com.testingai.hibernate.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.hibernate.entity.Librarian;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.repository.PersonRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(PersonDirectoryService.class)
class PersonDirectoryServiceTest {

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private PersonDirectoryService personDirectoryService;

	@BeforeEach
	void seedOneMemberAndOneLibrarian() {
		Member member = new Member();
		member.setName("Ada Lovelace");
		member.setEmail("ada@example.com");
		member.setMembershipDate(LocalDate.of(2020, 1, 1));
		personRepository.save(member);

		Librarian librarian = new Librarian();
		librarian.setName("Marian the Librarian");
		librarian.setEmail("marian@example.com");
		librarian.setEmployeeId("EMP-001");
		librarian.setDepartment("Reference");
		personRepository.save(librarian);
	}

	@Test
	void listAllPeopleReturnsBothSubtypesPolymorphicallyAcrossTheJoinedTables() {
		assertThat(personDirectoryService.listAllPeople()).hasSize(2)
				.extracting(PersonSummary::personType)
				.containsExactlyInAnyOrder("MEMBER", "LIBRARIAN");
	}
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=LibraryCatalogServiceTest,PersonDirectoryServiceTest`
Expected: FAIL — none of the classes in this task exist yet.

- [ ] **Step 3: Implement `LibraryCatalogService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/LibraryItemSummary.java`:

```java
package com.testingai.hibernate.inheritance;

public record LibraryItemSummary(Long id, String title, int publicationYear, String itemType) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/LibraryCatalogService.java`:

```java
package com.testingai.hibernate.inheritance;

import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Dvd;
import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.entity.Magazine;
import com.testingai.hibernate.repository.LibraryItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryCatalogService {

	private final LibraryItemRepository libraryItemRepository;

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listAll() {
		return libraryItemRepository.findAll().stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listBooks() {
		return libraryItemRepository.findAll().stream()
				.filter(Book.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listDvds() {
		return libraryItemRepository.findAll().stream()
				.filter(Dvd.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LibraryItemSummary> listMagazines() {
		return libraryItemRepository.findAll().stream()
				.filter(Magazine.class::isInstance)
				.map(this::toSummary)
				.toList();
	}

	private LibraryItemSummary toSummary(LibraryItem item) {
		String itemType = switch (item) {
			case Book b -> "BOOK";
			case Dvd d -> "DVD";
			case Magazine m -> "MAGAZINE";
			default -> throw new IllegalStateException("Unknown LibraryItem subtype: " + item.getClass());
		};
		return new LibraryItemSummary(item.getId(), item.getTitle(), item.getPublicationYear(), itemType);
	}
}
```

- [ ] **Step 4: Implement `PersonDirectoryService`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/PersonSummary.java`:

```java
package com.testingai.hibernate.inheritance;

public record PersonSummary(Long id, String name, String personType, String detail) {
}
```

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance/PersonDirectoryService.java`:

```java
package com.testingai.hibernate.inheritance;

import com.testingai.hibernate.entity.Librarian;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.entity.Person;
import com.testingai.hibernate.repository.PersonRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonDirectoryService {

	private final PersonRepository personRepository;

	@Transactional(readOnly = true)
	public List<PersonSummary> listAllPeople() {
		return personRepository.findAll().stream().map(this::toSummary).toList();
	}

	private PersonSummary toSummary(Person person) {
		return switch (person) {
			case Member m -> new PersonSummary(m.getId(), m.getName(), "MEMBER", m.getMembershipDate().toString());
			case Librarian l -> new PersonSummary(l.getId(), l.getName(), "LIBRARIAN", l.getDepartment());
			default -> throw new IllegalStateException("Unknown Person subtype: " + person.getClass());
		};
	}
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=LibraryCatalogServiceTest,PersonDirectoryServiceTest`
Expected: PASS (5 tests total)

- [ ] **Step 6: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/inheritance data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/inheritance
git commit -m "feat(hibernate): add inheritance/ polymorphic query demo over both hierarchies

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 7: `controller/` — DemoController

**Files:**
- Create: `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/controller/DemoController.java`
- Test: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: every service produced by Tasks 3-6, plus `AuthorRepository`, `BookRepository`, `LibraryItemRepository`, `CopyRepository`, `MemberRepository` (Task 2) for the seed endpoints.
- Produces: the full REST surface documented in Task 9's README.

- [ ] **Step 1: Write the failing controller test**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/controller/DemoControllerTest.java`:

```java
package com.testingai.hibernate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.hibernate.associations.BookAuthorService;
import com.testingai.hibernate.associations.BookFetchResult;
import com.testingai.hibernate.associations.CopyBatchFetchService;
import com.testingai.hibernate.associations.ItemFetchResult;
import com.testingai.hibernate.caching.AuthorCacheService;
import com.testingai.hibernate.caching.L1Result;
import com.testingai.hibernate.caching.L2Result;
import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.inheritance.LibraryCatalogService;
import com.testingai.hibernate.inheritance.PersonDirectoryService;
import com.testingai.hibernate.locking.CopyCheckoutService;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthorRepository authorRepository;

	@MockitoBean
	private BookRepository bookRepository;

	@MockitoBean
	private LibraryItemRepository libraryItemRepository;

	@MockitoBean
	private CopyRepository copyRepository;

	@MockitoBean
	private MemberRepository memberRepository;

	@MockitoBean
	private BookAuthorService bookAuthorService;

	@MockitoBean
	private CopyBatchFetchService copyBatchFetchService;

	@MockitoBean
	private AuthorCacheService authorCacheService;

	@MockitoBean
	private CopyCheckoutService copyCheckoutService;

	@MockitoBean
	private LibraryCatalogService libraryCatalogService;

	@MockitoBean
	private PersonDirectoryService personDirectoryService;

	@Test
	void createAuthorReturns200() throws Exception {
		Author author = new Author();
		author.setId(1L);
		author.setName("Ada Lovelace");
		when(authorRepository.save(any())).thenReturn(author);

		mockMvc.perform(post("/demo/authors")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new DemoController.CreateAuthorRequest("Ada Lovelace"))))
				.andExpect(status().isOk());
	}

	@Test
	void demonstrateNPlusOneReturns200() throws Exception {
		when(bookAuthorService.demonstrateNPlusOne()).thenReturn(new BookFetchResult(List.of(), 0));

		mockMvc.perform(get("/demo/books/n-plus-one")).andExpect(status().isOk());
	}

	@Test
	void demonstrateBatchFetchReturns200() throws Exception {
		when(copyBatchFetchService.demonstrateBatchFetch()).thenReturn(new ItemFetchResult(List.of(), 0));

		mockMvc.perform(get("/demo/library-items/batch-fetch")).andExpect(status().isOk());
	}

	@Test
	void readTwiceInSameTransactionReturns200() throws Exception {
		when(authorCacheService.readTwiceInSameTransaction(1L)).thenReturn(new L1Result("a", "a", 1));

		mockMvc.perform(get("/demo/authors/1/cache/l1")).andExpect(status().isOk());
	}

	@Test
	void readAcrossTwoTransactionsReturns200() throws Exception {
		when(authorCacheService.readAcrossTwoTransactions(1L)).thenReturn(new L2Result(1, 0, 1, 1));

		mockMvc.perform(get("/demo/authors/1/cache/l2")).andExpect(status().isOk());
	}

	@Test
	void checkoutOptimisticReturns200() throws Exception {
		Loan loan = new Loan();
		loan.setId(1L);
		when(copyCheckoutService.checkoutOptimistic(1L, 1L)).thenReturn(loan);

		mockMvc.perform(post("/demo/copies/1/checkout/optimistic?memberId=1"))
				.andExpect(status().isOk());
	}

	@Test
	void checkoutPessimisticReturns200() throws Exception {
		Loan loan = new Loan();
		loan.setId(1L);
		when(copyCheckoutService.checkoutPessimistic(1L, 1L)).thenReturn(loan);

		mockMvc.perform(post("/demo/copies/1/checkout/pessimistic?memberId=1"))
				.andExpect(status().isOk());
	}

	@Test
	void listAllLibraryItemsReturns200() throws Exception {
		when(libraryCatalogService.listAll()).thenReturn(List.of());

		mockMvc.perform(get("/demo/library-items")).andExpect(status().isOk());
	}

	@Test
	void listAllPeopleReturns200() throws Exception {
		when(personDirectoryService.listAllPeople()).thenReturn(List.of());

		mockMvc.perform(get("/demo/people")).andExpect(status().isOk());
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: FAIL — `DemoController` does not exist yet.

- [ ] **Step 3: Implement `DemoController`**

Create `data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/controller/DemoController.java`:

```java
package com.testingai.hibernate.controller;

import com.testingai.hibernate.associations.BookAuthorService;
import com.testingai.hibernate.associations.BookFetchResult;
import com.testingai.hibernate.associations.CopyBatchFetchService;
import com.testingai.hibernate.associations.ItemFetchResult;
import com.testingai.hibernate.caching.AuthorCacheService;
import com.testingai.hibernate.caching.L1Result;
import com.testingai.hibernate.caching.L2Result;
import com.testingai.hibernate.entity.Address;
import com.testingai.hibernate.entity.Author;
import com.testingai.hibernate.entity.Book;
import com.testingai.hibernate.entity.Copy;
import com.testingai.hibernate.entity.CopyStatus;
import com.testingai.hibernate.entity.Isbn;
import com.testingai.hibernate.entity.LibraryItem;
import com.testingai.hibernate.entity.Loan;
import com.testingai.hibernate.entity.Member;
import com.testingai.hibernate.inheritance.LibraryCatalogService;
import com.testingai.hibernate.inheritance.LibraryItemSummary;
import com.testingai.hibernate.inheritance.PersonDirectoryService;
import com.testingai.hibernate.inheritance.PersonSummary;
import com.testingai.hibernate.locking.CopyCheckoutService;
import com.testingai.hibernate.repository.AuthorRepository;
import com.testingai.hibernate.repository.BookRepository;
import com.testingai.hibernate.repository.CopyRepository;
import com.testingai.hibernate.repository.LibraryItemRepository;
import com.testingai.hibernate.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

	private final AuthorRepository authorRepository;
	private final BookRepository bookRepository;
	private final LibraryItemRepository libraryItemRepository;
	private final CopyRepository copyRepository;
	private final MemberRepository memberRepository;
	private final BookAuthorService bookAuthorService;
	private final CopyBatchFetchService copyBatchFetchService;
	private final AuthorCacheService authorCacheService;
	private final CopyCheckoutService copyCheckoutService;
	private final LibraryCatalogService libraryCatalogService;
	private final PersonDirectoryService personDirectoryService;

	public record CreateAuthorRequest(String name) {
	}

	public record CreateBookRequest(String title, int publicationYear, String isbn, List<Long> authorIds) {
	}

	public record CreateCopyRequest(Long libraryItemId, String barcode) {
	}

	public record CreateMemberRequest(
			String name, String email, LocalDate membershipDate, String street, String city, String zipCode) {
	}

	@PostMapping("/authors")
	public Author createAuthor(@RequestBody CreateAuthorRequest request) {
		Author author = new Author();
		author.setName(request.name());
		return authorRepository.save(author);
	}

	@PostMapping("/books")
	public Book createBook(@RequestBody CreateBookRequest request) {
		Book book = new Book();
		book.setTitle(request.title());
		book.setPublicationYear(request.publicationYear());
		if (request.isbn() != null) {
			book.setIsbn(new Isbn(request.isbn()));
		}
		if (request.authorIds() != null && !request.authorIds().isEmpty()) {
			book.setAuthors(Set.copyOf(authorRepository.findAllById(request.authorIds())));
		}
		return bookRepository.save(book);
	}

	@PostMapping("/copies")
	public Copy createCopy(@RequestBody CreateCopyRequest request) {
		LibraryItem item = libraryItemRepository.findById(request.libraryItemId())
				.orElseThrow(() -> new NoSuchElementException("Library item not found: " + request.libraryItemId()));
		Copy copy = new Copy();
		copy.setLibraryItem(item);
		copy.setBarcode(request.barcode());
		copy.setStatus(CopyStatus.AVAILABLE);
		return copyRepository.save(copy);
	}

	@PostMapping("/members")
	public Member createMember(@RequestBody CreateMemberRequest request) {
		Member member = new Member();
		member.setName(request.name());
		member.setEmail(request.email());
		member.setMembershipDate(request.membershipDate());
		member.setAddress(new Address(request.street(), request.city(), request.zipCode()));
		return memberRepository.save(member);
	}

	@GetMapping("/books/n-plus-one")
	public BookFetchResult demonstrateNPlusOne() {
		return bookAuthorService.demonstrateNPlusOne();
	}

	@GetMapping("/books/join-fetch")
	public BookFetchResult findAllWithJoinFetch() {
		return bookAuthorService.findAllWithJoinFetch();
	}

	@GetMapping("/books/entity-graph")
	public BookFetchResult findAllWithEntityGraph() {
		return bookAuthorService.findAllWithEntityGraph();
	}

	@GetMapping("/library-items/batch-fetch")
	public ItemFetchResult demonstrateBatchFetch() {
		return copyBatchFetchService.demonstrateBatchFetch();
	}

	@GetMapping("/authors/{id}/cache/l1")
	public L1Result readTwiceInSameTransaction(@PathVariable Long id) {
		return authorCacheService.readTwiceInSameTransaction(id);
	}

	@GetMapping("/authors/{id}/cache/l2")
	public L2Result readAcrossTwoTransactions(@PathVariable Long id) {
		return authorCacheService.readAcrossTwoTransactions(id);
	}

	@PostMapping("/copies/{copyId}/checkout/optimistic")
	public Loan checkoutOptimistic(@PathVariable Long copyId, @RequestParam Long memberId) {
		return copyCheckoutService.checkoutOptimistic(copyId, memberId);
	}

	@PostMapping("/copies/{copyId}/checkout/pessimistic")
	public Loan checkoutPessimistic(@PathVariable Long copyId, @RequestParam Long memberId) {
		return copyCheckoutService.checkoutPessimistic(copyId, memberId);
	}

	@GetMapping("/library-items")
	public List<LibraryItemSummary> listAllLibraryItems() {
		return libraryCatalogService.listAll();
	}

	@GetMapping("/library-items/books")
	public List<LibraryItemSummary> listBooks() {
		return libraryCatalogService.listBooks();
	}

	@GetMapping("/library-items/dvds")
	public List<LibraryItemSummary> listDvds() {
		return libraryCatalogService.listDvds();
	}

	@GetMapping("/library-items/magazines")
	public List<LibraryItemSummary> listMagazines() {
		return libraryCatalogService.listMagazines();
	}

	@GetMapping("/people")
	public List<PersonSummary> listAllPeople() {
		return personDirectoryService.listAllPeople();
	}

	@ExceptionHandler(com.testingai.hibernate.locking.CopyAlreadyLoanedException.class)
	public ResponseEntity<String> handleCopyAlreadyLoaned(com.testingai.hibernate.locking.CopyAlreadyLoanedException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=DemoControllerTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Run the full test suite**

Run: `mvn test`
Expected: PASS — all unit tests green (`CopyCheckoutServiceConcurrencyIT` and Gatling remain excluded).

- [ ] **Step 6: Commit**

```bash
git add data-access/hibernate/spring-demo/src/main/java/com/testingai/hibernate/controller data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/controller
git commit -m "feat(hibernate): add DemoController wiring all patterns to REST endpoints

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 8: Gatling load test

**Files:**
- Create: `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: the running app's `/demo/*` endpoints (Task 7). No production code interface — this is a standalone load-test scenario.

- [ ] **Step 1: Write the simulation**

Create `data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/performance/DemoSimulation.java`:

```java
package com.testingai.hibernate.performance;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

public class DemoSimulation extends Simulation {

	private final HttpProtocolBuilder httpProtocol =
			http.baseUrl("http://localhost:8105").acceptHeader("application/json");

	private final ScenarioBuilder catalogAndFetchScenario = scenario("Catalog and association fetching")
			.exec(http("List all library items").get("/demo/library-items").check(status().is(200)))
			.exec(http("List all people").get("/demo/people").check(status().is(200)))
			.exec(http("Demonstrate N+1").get("/demo/books/n-plus-one").check(status().is(200)))
			.exec(http("Fetch with JOIN FETCH").get("/demo/books/join-fetch").check(status().is(200)))
			.exec(http("Fetch with @EntityGraph").get("/demo/books/entity-graph").check(status().is(200)))
			.exec(http("Fetch with @BatchSize").get("/demo/library-items/batch-fetch").check(status().is(200)));

	{
		setUp(catalogAndFetchScenario.injectOpen(constantUsersPerSec(10).during(Duration.ofSeconds(30))))
				.protocols(httpProtocol);
	}
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add data-access/hibernate/spring-demo/src/test/java/com/testingai/hibernate/performance
git commit -m "feat(hibernate): add Gatling load test

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```

---

## Task 9: Documentation

**Files:**
- Create: `data-access/hibernate/README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- None — documentation only.

- [ ] **Step 1: Write the module README**

Create `data-access/hibernate/README.md`:

```markdown
# Hibernate Demo

A Spring Boot app demonstrating Hibernate/JPA mechanics that plain CRUD doesn't exercise, over a small library-lending domain (`author`, `library_item` [Book/Dvd/Magazine], `copy`, `person` [Member/Librarian], `loan`).

## Prerequisites

- Java 21, Maven
- Docker (only for the `postgres` profile and the one `CopyCheckoutServiceConcurrencyIT` test)

## Build, test, run

\`\`\`bash
cd data-access/hibernate/spring-demo

mvn clean package                                        # build
mvn test                                                   # unit tests (H2) — no Docker needed
mvn test -Dtest=CopyCheckoutServiceConcurrencyIT             # the one Postgres-only test — needs a Docker daemon (Testcontainers)
mvn spring-boot:run                                         # run against H2, no Docker — app on :8105
\`\`\`

To run against real Postgres instead:

\`\`\`bash
docker compose -f data-access/hibernate/docker/docker-compose.yml up -d   # Postgres on :5435
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
\`\`\`

## Patterns and endpoints

| Pattern | Endpoint(s) |
|---|---|
| Seed data | `POST /demo/authors`, `POST /demo/books`, `POST /demo/copies`, `POST /demo/members` |
| `associations` — N+1, `JOIN FETCH`, `@EntityGraph`, `@BatchSize` | `GET /demo/books/n-plus-one`, `GET /demo/books/join-fetch`, `GET /demo/books/entity-graph`, `GET /demo/library-items/batch-fetch` |
| `caching` — L1 vs L2 | `GET /demo/authors/{id}/cache/l1`, `GET /demo/authors/{id}/cache/l2` |
| `locking` — optimistic vs pessimistic | `POST /demo/copies/{copyId}/checkout/optimistic?memberId=`, `POST /demo/copies/{copyId}/checkout/pessimistic?memberId=` (may 409 if the copy is already on loan, or roll back on a simulated 5% failure) |
| `inheritance` — polymorphic queries | `GET /demo/library-items`, `GET /demo/library-items/books`, `GET /demo/library-items/dvds`, `GET /demo/library-items/magazines`, `GET /demo/people` |

Swagger UI: `http://localhost:8105/swagger-ui/index.html`.

## Load testing

\`\`\`bash
mvn gatling:test    # requires the app running first (default H2 profile is fine)
\`\`\`
```

- [ ] **Step 2: Update `CLAUDE.md`**

Modify `CLAUDE.md`: add a new command block after the jOOQ data-access demo section (find the `### jOOQ data-access demo` heading and its fenced code block, then insert immediately after it):

```markdown
### Hibernate data-access demo (run from the module root, Docker only needed for the `postgres` profile and one test)

\`\`\`bash
cd data-access/hibernate/spring-demo

mvn clean package                          # build
mvn test                                    # unit tests against H2 (Gatling and CopyCheckoutServiceConcurrencyIT excluded automatically)
mvn test -Dtest=CopyCheckoutServiceConcurrencyIT   # the one Postgres-only test — requires a Docker daemon (Testcontainers)
mvn spring-boot:run                          # run the app against H2 (:8105)
docker compose -f data-access/hibernate/docker/docker-compose.yml up -d   # Postgres on :5435, for the postgres profile
mvn spring-boot:run -Dspring-boot.run.profiles=postgres               # run against real Postgres
mvn gatling:test                            # load test — requires the app running first
\`\`\`
```

Also add a row to the repository-layout table (find the row for `data-access/jooq/spring-demo/` and add immediately after it):

```markdown
| `data-access/hibernate/spring-demo/` | Hibernate/JPA demo app, same conventions as `data-access/jooq/spring-demo/` — association fetching and the N+1 problem, first/second-level caching, optimistic/pessimistic locking, and inheritance/embeddable/converter/auditing mapping over a library-lending domain (`author`, `library_item` [SINGLE_TABLE: Book/Dvd/Magazine], `copy`, `person` [JOINED: Member/Librarian], `loan`); `mvn test` needs no external infrastructure, but the one concurrency IT and the `postgres` profile need `docker compose -f data-access/hibernate/docker/docker-compose.yml up -d` first |
```

- [ ] **Step 3: Commit**

```bash
git add data-access/hibernate/README.md CLAUDE.md
git commit -m "docs(hibernate): add module README and CLAUDE.md commands

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FogQpnBACDZiyabPxutFVc"
```
