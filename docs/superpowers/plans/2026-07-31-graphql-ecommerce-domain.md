# GraphQL Demo E-Commerce Domain Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `communication-protocols/graphql/spring-demo` from a Products/Reviews-only, in-memory demo into a Postgres-backed e-commerce domain (Category, User, Order, OrderItem), broadening GraphQL pattern coverage: real persistence, DB-pushed-down keyset pagination at 10k-product scale, `@BatchMapping` vs. manual `DataLoader` side by side, row-level authorization, and a transactional multi-row mutation.

**Architecture:** JPA entities (`com.testingai.graphql.entity`) + Spring Data repositories persist to Postgres (Docker) / H2-in-Postgres-mode (tests); the GraphQL-facing layer stays plain records (`com.testingai.graphql.domain`), mapped from entities by per-entity services, exactly as `ProductCatalogService`/`ReviewService` already work today. Every relation gets its own DataLoader batch query — `@BatchMapping` where no `@Argument` is needed, manual `BatchLoaderRegistry` registration where pagination/filter arguments are required.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring for GraphQL, Spring Data JPA, Liquibase, Postgres 15 (prod), H2 (test, `MODE=PostgreSQL`), Lombok, JUnit 5 + AssertJ + Mockito, Spring GraphQL Test (`HttpGraphQlTester`/`WebSocketGraphQlTester`).

## Global Constraints

- Design spec: `docs/superpowers/specs/2026-07-31-graphql-ecommerce-domain-design.md` — every task below implements a section of it; treat it as authoritative for anything not restated here.
- Module root for all commands: `communication-protocols/graphql/spring-demo` (run `mvn` from `communication-protocols/` with `-pl graphql/spring-demo`, per this repo's existing convention).
- `mvn test` must stay Docker-free (H2 in Postgres-compatibility mode via `src/test/resources/application.yml`, which Spring Boot picks up automatically on the test classpath ahead of the main one — no `@ActiveProfiles` needed).
- Seed volumes are configuration (`app.seed.*`), not hardcoded: 100 users / 100 categories / 10,000 products / 3–10 reviews per product / 3,000 orders in the default profile; 10 / 10 / 50 / 1–3 / 20 in the test profile.
- Existing conventions to follow, not reinvent: tab indentation (all existing files use tabs, not spaces), Lombok `@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor` on entities (mirrors `backend/rest-api`'s `Post` entity — the only other JPA entity precedent in this repo), `@Slf4j` + constructor injection (no field `@Autowired`) on services/controllers, AssertJ `assertThat` (no Hamcrest/JUnit asserts), one Liquibase changelog file per table named `db.changelog-N-create-X-table.xml`.
- `FailureSimulator`, `CursorPagination`, `Connection`/`Edge`/`PageInfo`, `DemoExceptionResolver`, and `SecurityConfig` are **not modified** by this plan — every new failure path reuses them as-is (e.g. `InsufficientStockException extends IllegalArgumentException` specifically so `DemoExceptionResolver`'s existing classification handles it with zero changes).

---

### Task 1: Persistence infrastructure & config

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/pom.xml`
- Create: `communication-protocols/graphql/docker/docker-compose.yml`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/application.yml`
- Create: `communication-protocols/graphql/spring-demo/src/test/resources/application.yml`
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `CLAUDE.md` (root)

**Interfaces:**
- Produces: `classpath:db/changelog/db.changelog-master.xml` (empty root changelog — later tasks add `<include>` entries), the `app.seed.*` property namespace (consumed by `SeedProperties` in Task 5), a running Postgres reachable at `localhost:5433/graphqldemo` for the live app, and an H2-in-Postgres-mode datasource for `mvn test`.

- [ ] **Step 1: Add persistence dependencies to `pom.xml`**

Insert into the `<dependencies>` block (after the existing `spring-boot-starter-security` dependency):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

No explicit `<version>` needed for any of these — all four are managed by the `spring-boot-starter-parent:3.4.4` BOM (the parent of `communication-protocols`'s own parent chain; verify this by checking that `backend/rest-api/pom.xml`, which uses the same starters, also omits versions for them).

- [ ] **Step 2: Create the module's Postgres compose file**

`communication-protocols/graphql/docker/docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:15
    container_name: graphql-demo-postgres
    ports:
      - "5433:5432"
    environment:
      - POSTGRES_DB=graphqldemo
      - POSTGRES_USER=graphql
      - POSTGRES_PASSWORD=graphql
    volumes:
      - graphql_demo_postgres_data:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  graphql_demo_postgres_data:
```

Port `5433` (not `5432`) deliberately avoids clashing with the shared stack's Postgres container defined in the repo root `docker-compose.yml`.

- [ ] **Step 3: Configure the main datasource, JPA, Liquibase, and seed properties**

Replace the full contents of `communication-protocols/graphql/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8092

spring:
  graphql:
    graphiql:
      enabled: true
    websocket:
      path: /graphql
  datasource:
    url: jdbc:postgresql://localhost:5433/graphqldemo
    username: graphql
    password: graphql
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 500
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

app:
  seed:
    enabled: true
    user-count: 100
    category-count: 100
    product-count: 10000
    min-reviews-per-product: 3
    max-reviews-per-product: 10
    order-count: 3000
```

`ddl-auto: validate` (not `update`, unlike `backend/rest-api`'s `Post` entity): the schema is fully owned by Liquibase from the start here, so Hibernate should only verify entity mappings match it, not silently patch the schema itself. `open-in-view: false` forces every lazy association to be resolved inside its owning `@Transactional` service method — the same discipline the design doc's `placeOrder` transaction relies on — rather than accidentally working by accident of an open Hibernate session reaching into the web layer.

- [ ] **Step 4: Configure the test datasource (H2, Postgres-compatibility mode)**

Create `communication-protocols/graphql/spring-demo/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:graphqldemo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

app:
  seed:
    enabled: true
    user-count: 10
    category-count: 10
    product-count: 50
    min-reviews-per-product: 1
    max-reviews-per-product: 3
    order-count: 20
```

A file named `application.yml` under `src/test/resources` is on the test classpath ahead of the main one, so Spring Boot loads this instead of `src/main/resources/application.yml` for every test in the module automatically — no `@ActiveProfiles` annotation needed on any test class, existing or new.

- [ ] **Step 5: Create the (initially empty) Liquibase master changelog**

`communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

</databaseChangeLog>
```

Zero `<include>` entries is a valid, meaningful Liquibase changelog (Liquibase runs, creates its bookkeeping tables, applies nothing) — Tasks 2–4 each add one `<include>` line per table as they introduce it.

- [ ] **Step 6: Verify the module still builds and its context still loads**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=GraphQlSpringDemoApplicationTest`
Expected: `BUILD SUCCESS` — Spring Boot starts against the H2 datasource, Liquibase runs with no changesets, `contextLoads()` passes.

- [ ] **Step 7: Add the docker-compose step to the module's run instructions in root `CLAUDE.md`**

In the `### GraphQL communication protocol demo` command block (currently lines 78–87), insert a new line directly above `mvn -pl graphql/spring-demo spring-boot:run`:

```bash
docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d   # Postgres :5433
mvn -pl graphql/spring-demo spring-boot:run              # run the app (GraphiQL at :8092/graphiql)
```

Also update the one-line module description in the repository-layout table (currently line 207) to drop "no external infrastructure required" (no longer true for *running* the app, though `mvn test` still needs none):

```
| `communication-protocols/graphql/spring-demo/` | GraphQL demo — single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, subscription, cursor-pagination, and an e-commerce domain (Category/User/Order) against Postgres; `mvn test` needs no external infrastructure (H2), but running the app needs `docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d` first |
```

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/graphql/spring-demo/pom.xml communication-protocols/graphql/docker/docker-compose.yml communication-protocols/graphql/spring-demo/src/main/resources/application.yml communication-protocols/graphql/spring-demo/src/test/resources/application.yml communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml CLAUDE.md
git commit -m "feat(communication-protocols): add Postgres/Liquibase persistence infra to graphql demo"
```

---

### Task 2: User & Category persistence

**Files:**
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-1-create-users-table.xml`
- Create: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-2-create-categories-table.xml`
- Modify: `communication-protocols/graphql/spring-demo/src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Role.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/entity/UserEntity.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/entity/CategoryEntity.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/repository/UserRepository.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/repository/CategoryRepository.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/User.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Category.java`
- Create: `communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/UserService.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/repository/UserRepositoryTest.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/repository/CategoryRepositoryTest.java`
- Test: `communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/UserServiceTest.java`

**Interfaces:**
- Consumes: nothing beyond Task 1's persistence config.
- Produces: `UserEntity`, `CategoryEntity` (self-referencing via `parent`), `UserRepository.findByUsername(String): Optional<UserEntity>`, `CategoryRepository.findByParentId(Long)`/`findByParentIdIn(List<Long>)`, `User(Long id, String username, String displayName, Role role)`, `Category(Long id, String name, Long parentId)`, `UserService.findByUsername(String): Optional<User>` and `UserService.findByIds(List<Long>): Map<Long, User>` — both consumed by Tasks 3 and 7.

- [ ] **Step 1: Write the failing repository tests**

`.../repository/UserRepositoryTest.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.domain.Role;
import com.testingai.graphql.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void findByUsername_returnsUser_whenExists() {
		UserEntity user = new UserEntity();
		user.setUsername("jordan");
		user.setEmail("jordan@example.com");
		user.setDisplayName("Jordan");
		user.setRole(Role.CUSTOMER);
		userRepository.save(user);

		assertThat(userRepository.findByUsername("jordan")).isPresent().get()
				.extracting(UserEntity::getDisplayName).isEqualTo("Jordan");
	}

	@Test
	void findByUsername_returnsEmpty_whenUnknown() {
		assertThat(userRepository.findByUsername("unknown")).isEmpty();
	}
}
```

`@AutoConfigureTestDatabase(replace = Replace.NONE)` is required on every `@DataJpaTest` in this plan: without it, Spring Boot silently swaps in its own auto-configured embedded database instead of the `MODE=PostgreSQL` H2 datasource configured in Task 1's `src/test/resources/application.yml`, which would both skip that Postgres-compatibility setting and (more importantly) run Liquibase against a different, undocumented database than the rest of the test suite.

`.../repository/CategoryRepositoryTest.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest {

	@Autowired
	private CategoryRepository categoryRepository;

	@Test
	void findByParentId_returnsChildren() {
		CategoryEntity root = new CategoryEntity();
		root.setName("Electronics");
		root = categoryRepository.save(root);

		CategoryEntity child = new CategoryEntity();
		child.setName("Audio");
		child.setParent(root);
		categoryRepository.save(child);

		List<CategoryEntity> children = categoryRepository.findByParentId(root.getId());

		assertThat(children).extracting(CategoryEntity::getName).containsExactly("Audio");
	}

	@Test
	void rootCategory_hasNullParent() {
		CategoryEntity root = new CategoryEntity();
		root.setName("Electronics");
		CategoryEntity saved = categoryRepository.save(root);

		assertThat(categoryRepository.findById(saved.getId())).get().extracting(CategoryEntity::getParent).isNull();
	}
}
```

- [ ] **Step 2: Run the tests to verify they fail to compile**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=UserRepositoryTest,CategoryRepositoryTest`
Expected: compile error — `UserEntity`, `CategoryEntity`, `UserRepository`, `CategoryRepository`, `Role` don't exist yet.

- [ ] **Step 3: Add the Liquibase changesets**

`.../db.changelog-1-create-users-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="1-create-users-table" author="migration">
        <createTable tableName="users">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="username" type="VARCHAR(255)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="email" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="display_name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="role" type="VARCHAR(20)">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

`.../db.changelog-2-create-categories-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="2-create-categories-table" author="migration">
        <createTable tableName="categories">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="parent_id" type="BIGINT">
                <constraints nullable="true" foreignKeyName="fk_categories_parent" references="categories(id)"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

Update `db.changelog-master.xml` to include both:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <include file="db/changelog/db.changelog-1-create-users-table.xml"/>
    <include file="db/changelog/db.changelog-2-create-categories-table.xml"/>

</databaseChangeLog>
```

- [ ] **Step 4: Write `Role`, the entities, and the repositories**

`.../domain/Role.java`:

```java
package com.testingai.graphql.domain;

public enum Role {
	CUSTOMER, ADMIN
}
```

`.../entity/UserEntity.java`:

```java
package com.testingai.graphql.entity;

import com.testingai.graphql.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;
}
```

`.../entity/CategoryEntity.java`:

```java
package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "categories")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CategoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private CategoryEntity parent;
}
```

`.../repository/UserRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
	Optional<UserEntity> findByUsername(String username);
}
```

`.../repository/CategoryRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long>, JpaSpecificationExecutor<CategoryEntity> {
	List<CategoryEntity> findByParentId(Long parentId);
	List<CategoryEntity> findByParentIdIn(List<Long> parentIds);
}
```

- [ ] **Step 5: Run the repository tests again**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=UserRepositoryTest,CategoryRepositoryTest`
Expected: `BUILD SUCCESS`, both test classes pass.

- [ ] **Step 6: Write the GraphQL-facing `User`/`Category` records and the failing `UserService` test**

`.../domain/User.java`:

```java
package com.testingai.graphql.domain;

public record User(Long id, String username, String displayName, Role role) {
}
```

`.../domain/Category.java`:

```java
package com.testingai.graphql.domain;

public record Category(Long id, String name, Long parentId) {
}
```

`.../domain/UserServiceTest.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserServiceTest.Config.class)
class UserServiceTest {

	@TestConfiguration
	static class Config {
		@Bean
		UserService userService(UserRepository userRepository) {
			return new UserService(userRepository);
		}
	}

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserService userService;

	@Test
	void findByUsername_mapsEntityToRecord() {
		UserEntity entity = new UserEntity();
		entity.setUsername("jordan");
		entity.setEmail("jordan@example.com");
		entity.setDisplayName("Jordan");
		entity.setRole(Role.CUSTOMER);
		userRepository.save(entity);

		assertThat(userService.findByUsername("jordan")).isPresent().get()
				.satisfies(user -> {
					assertThat(user.username()).isEqualTo("jordan");
					assertThat(user.displayName()).isEqualTo("Jordan");
					assertThat(user.role()).isEqualTo(Role.CUSTOMER);
				});
	}

	@Test
	void findByIds_returnsMapKeyedById() {
		UserEntity a = new UserEntity();
		a.setUsername("a");
		a.setEmail("a@example.com");
		a.setDisplayName("A");
		a.setRole(Role.CUSTOMER);
		UserEntity saved = userRepository.save(a);

		Map<Long, User> byId = userService.findByIds(List.of(saved.getId()));

		assertThat(byId).containsKey(saved.getId());
		assertThat(byId.get(saved.getId()).username()).isEqualTo("a");
	}
}
```

- [ ] **Step 7: Run it, verify it fails to compile (`UserService` doesn't exist), then write `UserService`**

`.../domain/UserService.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public Optional<User> findByUsername(String username) {
		return userRepository.findByUsername(username).map(UserService::toUser);
	}

	public Map<Long, User> findByIds(List<Long> ids) {
		return userRepository.findAllById(ids).stream().collect(Collectors.toMap(UserEntity::getId, UserService::toUser));
	}

	static User toUser(UserEntity entity) {
		return new User(entity.getId(), entity.getUsername(), entity.getDisplayName(), entity.getRole());
	}
}
```

- [ ] **Step 8: Run the full module test suite**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`. (Existing tests still reference the old in-memory `Product`/`Review` catalog and will still pass unchanged at this point — Task 3 is what touches them.)

- [ ] **Step 9: Commit**

```bash
git add communication-protocols/graphql/spring-demo/src/main/resources/db/changelog communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Role.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/User.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/Category.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/domain/UserService.java communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/entity communication-protocols/graphql/spring-demo/src/main/java/com/testingai/graphql/repository communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/repository communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/domain/UserServiceTest.java
git commit -m "feat(communication-protocols): add User and Category persistence to graphql demo"
```

---

### Task 3: Migrate Product/Review to Postgres; DB-pushed-down keyset pagination

**Files:**
- Create: `.../db.changelog-3-create-products-table.xml`
- Create: `.../db.changelog-4-create-product-categories-table.xml`
- Create: `.../db.changelog-5-create-reviews-table.xml`
- Modify: `.../db.changelog-master.xml`
- Create: `.../entity/ProductEntity.java`
- Create: `.../entity/ReviewEntity.java`
- Create: `.../repository/ProductRepository.java`
- Create: `.../repository/ProductSpecifications.java`
- Create: `.../repository/ReviewRepository.java`
- Create: `.../pagination/KeysetPagination.java`
- Test: `.../pagination/KeysetPaginationTest.java`
- Modify: `.../domain/Product.java` (add `stockQty`)
- Modify: `.../domain/Review.java` (`author: String` → `authorId: Long`)
- Modify: `.../domain/AddReviewInput.java` (drop `author`)
- Modify: `.../domain/ProductCatalogService.java` (full rewrite, DB-backed)
- Modify: `.../domain/ReviewService.java` (full rewrite, DB-backed)
- Modify: `.../controller/DemoController.java` (`products`, `product`, `addReview`, `author` batch mapping)
- Modify: `.../resources/graphql/schema.graphqls` (`Product.stockQty`, `Review.author: User!`, minimal `type User`/`enum Role`, `AddReviewInput` drops `author`)
- Delete: `.../test/java/com/testingai/graphql/controller/DemoControllerTest.java` (superseded — see Step 8)
- Modify: `.../domain/ProductCatalogServiceTest.java` (full rewrite)
- Modify: `.../domain/ReviewServiceTest.java` (full rewrite)
- Modify: `.../controller/DemoIntegrationTest.java` (queries/mutations updated for the new schema)

**Interfaces:**
- Consumes: `UserService`/`UserRepository` (Task 2) for `Review.author` resolution.
- Produces: `Product(String id, String name, long priceCents, int stockQty)`, `Review(String id, String productId, Long authorId, int rating, String comment)`, `ProductCatalogService.listProducts(ProductFilter, Integer first, String after): Connection<Product>`, `ProductCatalogService.findProduct(String): Optional<Product>`, `ProductCatalogService.findByIds(List<String>): Map<String, Product>` (consumed by Task 7's `OrderItem.product`), `KeysetPagination.paginate(...)` (consumed by Tasks 6 and 7).

- [ ] **Step 1: Write `KeysetPaginationTest` (pure unit test, no DB)**

`.../pagination/KeysetPaginationTest.java`:

```java
package com.testingai.graphql.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeysetPaginationTest {

	private record Row(Long id, String value) {
	}

	@Test
	void paginate_returnsEmptyConnection_forEmptyList() {
		Connection<String> connection = KeysetPagination.paginate(List.of(), 10, Row::id, Row::value, 0);

		assertThat(connection.edges()).isEmpty();
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
		assertThat(connection.totalCount()).isZero();
	}

	@Test
	void paginate_dropsExtraRow_andSetsHasNextPage_whenMoreRowsThanLimit() {
		List<Row> rowsLimitPlusOne = List.of(new Row(1L, "a"), new Row(2L, "b"), new Row(3L, "c"));

		Connection<String> connection = KeysetPagination.paginate(rowsLimitPlusOne, 2, Row::id, Row::value, 3);

		assertThat(connection.edges()).extracting(Edge::node).containsExactly("a", "b");
		assertThat(connection.pageInfo().hasNextPage()).isTrue();
		assertThat(connection.totalCount()).isEqualTo(3);
	}

	@Test
	void paginate_hasNoNextPage_whenRowsWithinLimit() {
		List<Row> rows = List.of(new Row(1L, "a"), new Row(2L, "b"));

		Connection<String> connection = KeysetPagination.paginate(rows, 10, Row::id, Row::value, 2);

		assertThat(connection.edges()).hasSize(2);
		assertThat(connection.pageInfo().hasNextPage()).isFalse();
	}

	@Test
	void decodeCursor_returnsNull_whenAfterIsNull() {
		assertThat(KeysetPagination.decodeCursor(null)).isNull();
	}

	@Test
	void decodeCursor_roundTrips_throughEncodedCursorFromPaginate() {
		List<Row> rows = List.of(new Row(5L, "a"), new Row(9L, "b"));

		Connection<String> connection = KeysetPagination.paginate(rows, 10, Row::id, Row::value, 2);

		assertThat(KeysetPagination.decodeCursor(connection.pageInfo().endCursor())).isEqualTo(9L);
	}

	@Test
	void decodeCursor_throws_whenCursorIsMalformed() {
		assertThatThrownBy(() -> KeysetPagination.decodeCursor("not-a-valid-cursor!!"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void normalizeFirst_defaultsToTen_whenOmitted() {
		assertThat(KeysetPagination.normalizeFirst(null)).isEqualTo(10);
	}

	@Test
	void normalizeFirst_clampsToFiftyMax() {
		assertThat(KeysetPagination.normalizeFirst(1000)).isEqualTo(50);
	}

	@Test
	void normalizeFirst_throws_whenNotPositive() {
		assertThatThrownBy(() -> KeysetPagination.normalizeFirst(0)).isInstanceOf(IllegalArgumentException.class);
	}
}
```

- [ ] **Step 2: Run it, verify it fails to compile, then write `KeysetPagination`**

`.../pagination/KeysetPagination.java`:

```java
package com.testingai.graphql.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * DB-pushed-down counterpart to {@link CursorPagination}: the caller fetches at most {@code limit + 1} rows already
 * filtered/sorted/limited by the database (typically {@code WHERE id > :cursorId ORDER BY id}), and this class only
 * turns that page into the Relay connection shape — unlike {@link CursorPagination}, it never loads or slices a full
 * result set itself, since avoiding exactly that is the point.
 */
public final class KeysetPagination {

	private static final String CURSOR_PREFIX = "keyset:";
	private static final int DEFAULT_FIRST = 10;
	private static final int MAX_FIRST = 50;

	private KeysetPagination() {
	}

	public static int normalizeFirst(Integer first) {
		if (first == null) {
			return DEFAULT_FIRST;
		}
		if (first <= 0) {
			throw new IllegalArgumentException("first must be positive, got " + first);
		}
		return Math.min(first, MAX_FIRST);
	}

	public static Long decodeCursor(String after) {
		if (after == null) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(after), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Malformed cursor: " + after, e);
		}
		if (!decoded.startsWith(CURSOR_PREFIX)) {
			throw new IllegalArgumentException("Malformed cursor: " + after);
		}
		try {
			return Long.parseLong(decoded.substring(CURSOR_PREFIX.length()));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed cursor: " + after, e);
		}
	}

	private static String encodeCursor(Long id) {
		return Base64.getEncoder().encodeToString((CURSOR_PREFIX + id).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * {@code rowsLimitPlusOne} must already be sorted by id ascending and contain at most {@code limit + 1} rows —
	 * the extra row, if present, is used only to compute {@code hasNextPage} and is excluded from the page.
	 */
	public static <E, T> Connection<T> paginate(List<E> rowsLimitPlusOne, int limit, Function<E, Long> idOf,
			Function<E, T> mapper, long totalCount) {
		boolean hasNextPage = rowsLimitPlusOne.size() > limit;
		List<E> page = hasNextPage ? rowsLimitPlusOne.subList(0, limit) : rowsLimitPlusOne;
		List<Edge<T>> edges = page.stream().map(row -> new Edge<>(mapper.apply(row), encodeCursor(idOf.apply(row))))
				.toList();
		String endCursor = edges.isEmpty() ? null : edges.getLast().cursor();
		return new Connection<>(edges, new PageInfo(hasNextPage, endCursor), (int) totalCount);
	}
}
```

- [ ] **Step 3: Run `KeysetPaginationTest` again**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=KeysetPaginationTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Add the Liquibase changesets for `products`, `product_categories`, `reviews`**

`.../db.changelog-3-create-products-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="3-create-products-table" author="migration">
        <createTable tableName="products">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="price_cents" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <column name="stock_qty" type="INT">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

`.../db.changelog-4-create-product-categories-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="4-create-product-categories-table" author="migration">
        <createTable tableName="product_categories">
            <column name="product_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_product_categories_product" references="products(id)"/>
            </column>
            <column name="category_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_product_categories_category" references="categories(id)"/>
            </column>
        </createTable>
        <addPrimaryKey tableName="product_categories" columnNames="product_id, category_id"
                constraintName="pk_product_categories"/>
    </changeSet>

</databaseChangeLog>
```

`.../db.changelog-5-create-reviews-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="5-create-reviews-table" author="migration">
        <createTable tableName="reviews">
            <column name="id" type="UUID">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="product_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_reviews_product" references="products(id)"/>
            </column>
            <column name="author_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_reviews_author" references="users(id)"/>
            </column>
            <column name="rating" type="INT">
                <constraints nullable="false"/>
            </column>
            <column name="comment" type="VARCHAR(1000)"/>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

Add the three `<include>` lines to `db.changelog-master.xml`, after the existing two.

- [ ] **Step 5: Write `ProductEntity`, `ReviewEntity`, their repositories, and `ProductSpecifications`**

`.../entity/ProductEntity.java`:

```java
package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "products")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "price_cents", nullable = false)
	private long priceCents;

	@Column(name = "stock_qty", nullable = false)
	private int stockQty;

	@ManyToMany
	@JoinTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "category_id"))
	@ToString.Exclude
	private Set<CategoryEntity> categories = new HashSet<>();
}
```

`.../entity/ReviewEntity.java`:

```java
package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	// Accessing .getId() on a lazy-loaded association never triggers a query in Hibernate — the FK column is
	// already part of this row, so ReviewService.toReview() reading author.getId() below is not an N+1.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private UserEntity author;

	@Column(nullable = false)
	private int rating;

	private String comment;
}
```

`.../repository/ProductRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ProductEntity p where p.id = :id")
	Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

	@Query("select distinct p from ProductEntity p left join fetch p.categories where p.id in :ids")
	List<ProductEntity> findByIdInWithCategories(@Param("ids") List<Long> ids);
}
```

`.../repository/ProductSpecifications.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.domain.ProductFilter;
import com.testingai.graphql.entity.ProductEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<ProductEntity> matching(ProductFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filter != null) {
				if (filter.nameContains() != null) {
					predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.nameContains().toLowerCase() + "%"));
				}
				if (filter.minPriceCents() != null) {
					predicates.add(cb.greaterThanOrEqualTo(root.get("priceCents"), filter.minPriceCents().longValue()));
				}
				if (filter.maxPriceCents() != null) {
					predicates.add(cb.lessThanOrEqualTo(root.get("priceCents"), filter.maxPriceCents().longValue()));
				}
			}
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	public static Specification<ProductEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}

	public static Specification<ProductEntity> inCategory(Long categoryId) {
		return (root, query, cb) -> {
			query.distinct(true);
			return cb.equal(root.join("categories").get("id"), categoryId);
		};
	}
}
```

`.../repository/ReviewRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ReviewEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {
	List<ReviewEntity> findByProductIdIn(List<Long> productIds);
}
```

- [ ] **Step 6: Update the `Product`/`Review`/`AddReviewInput` records**

`.../domain/Product.java`:

```java
package com.testingai.graphql.domain;

public record Product(String id, String name, long priceCents, int stockQty) {
}
```

`.../domain/Review.java`:

```java
package com.testingai.graphql.domain;

public record Review(String id, String productId, Long authorId, int rating, String comment) {
}
```

`.../domain/AddReviewInput.java`:

```java
package com.testingai.graphql.domain;

public record AddReviewInput(String productId, int rating, String comment) {
}
```

- [ ] **Step 7: Rewrite `ProductCatalogService` and `ReviewService` to be DB-backed**

`.../domain/ProductCatalogService.java` (full replacement):

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ProductSpecifications;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

	private final ProductRepository productRepository;

	public ProductCatalogService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public Optional<Product> findProduct(String productId) {
		return parseId(productId).flatMap(productRepository::findById).map(ProductCatalogService::toProduct);
	}

	public Connection<Product> listProducts(ProductFilter filter, Integer first, String after) {
		Long cursorId = KeysetPagination.decodeCursor(after);
		int limit = KeysetPagination.normalizeFirst(first);
		var spec = ProductSpecifications.matching(filter).and(ProductSpecifications.idAfter(cursorId));

		List<ProductEntity> rows = productRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
				.getContent();
		long totalCount = productRepository.count(ProductSpecifications.matching(filter));

		return KeysetPagination.paginate(rows, limit, ProductEntity::getId, ProductCatalogService::toProduct,
				totalCount);
	}

	public Map<String, Product> findByIds(List<String> productIds) {
		List<Long> ids = productIds.stream().map(Long::parseLong).toList();
		Map<String, Product> result = new LinkedHashMap<>();
		for (ProductEntity entity : productRepository.findAllById(ids)) {
			result.put(entity.getId().toString(), toProduct(entity));
		}
		return result;
	}

	private static Optional<Long> parseId(String productId) {
		try {
			return Optional.of(Long.parseLong(productId));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	static Product toProduct(ProductEntity entity) {
		return new Product(entity.getId().toString(), entity.getName(), entity.getPriceCents(), entity.getStockQty());
	}
}
```

This is the full `ProductCatalogService` for this task: just `findProduct`, `listProducts`, `findByIds`. Task 6 (Step 4) reopens this same file to add `findCategoriesByProductIds` and `listProductsInCategory` once `CategoryService`/`Category` exist — deferred rather than written speculatively here, so this task compiles and tests fully on its own.

`.../domain/ReviewService.java` (full replacement):

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ReviewEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	// directBestEffort(): delivered only to subscribers already connected at emission time — see the original
	// design rationale in this field's git history for why an onBackpressureBuffer() sink would be wrong here.
	private final Sinks.Many<Review> reviewAddedSink = Sinks.many().multicast().directBestEffort();
	private final AtomicInteger batchCallCount = new AtomicInteger();

	public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
			UserRepository userRepository) {
		this.reviewRepository = reviewRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
	}

	public Map<String, List<Review>> findByProductIds(List<String> productIds) {
		return findByProductIds(productIds, null);
	}

	@Transactional(readOnly = true)
	public Map<String, List<Review>> findByProductIds(List<String> productIds, ReviewFilter filter) {
		batchCallCount.incrementAndGet();
		log.info("batch fetching reviews for {} products in one call", productIds.size());
		List<Long> ids = productIds.stream().map(Long::parseLong).toList();
		Map<Long, List<Review>> byProductId = reviewRepository.findByProductIdIn(ids).stream()
				.map(ReviewService::toReview)
				.collect(Collectors.groupingBy(review -> Long.parseLong(review.productId()), LinkedHashMap::new,
						Collectors.toList()));

		Map<String, List<Review>> result = new LinkedHashMap<>();
		for (String productId : productIds) {
			List<Review> reviews = byProductId.getOrDefault(Long.parseLong(productId), List.of());
			result.put(productId, filterReviews(reviews, filter));
		}
		return result;
	}

	public List<Review> filterReviews(List<Review> reviews, ReviewFilter filter) {
		if (filter == null || filter.minRating() == null) {
			return reviews;
		}
		return reviews.stream().filter(review -> review.rating() >= filter.minRating()).toList();
	}

	@Transactional
	public Review addReview(String productId, Long authorId, int rating, String comment) {
		ProductEntity product = productRepository.findById(Long.parseLong(productId))
				.orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
		UserEntity author = userRepository.findById(authorId)
				.orElseThrow(() -> new NoSuchElementException("Unknown user: " + authorId));

		ReviewEntity entity = new ReviewEntity();
		entity.setProduct(product);
		entity.setAuthor(author);
		entity.setRating(rating);
		entity.setComment(comment);
		Review review = toReview(reviewRepository.save(entity));

		reviewAddedSink.tryEmitNext(review);
		return review;
	}

	public Flux<Review> reviewAdded() {
		return reviewAddedSink.asFlux();
	}

	@Transactional
	public boolean deleteReview(String reviewId) {
		UUID id;
		try {
			id = UUID.fromString(reviewId);
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (!reviewRepository.existsById(id)) {
			return false;
		}
		reviewRepository.deleteById(id);
		return true;
	}

	public int getBatchCallCount() {
		return batchCallCount.get();
	}

	static Review toReview(ReviewEntity entity) {
		return new Review(entity.getId().toString(), entity.getProduct().getId().toString(), entity.getAuthor().getId(),
				entity.getRating(), entity.getComment());
	}
}
```

- [ ] **Step 8: Update `DemoController`, `schema.graphqls`, delete the now-superseded `DemoControllerTest`**

In `.../resources/graphql/schema.graphqls`:

Add `stockQty: Int!` to `type Product`:

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    stockQty: Int!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
}
```

Change `Review.author` from `String!` to `User!`, and add the minimal `User`/`Role` types (no `orders` field yet — Task 7 adds it):

```graphql
type Review {
    id: ID!
    productId: ID!
    author: User!
    rating: Int!
    comment: String
}

type User {
    id: ID!
    username: String!
    displayName: String!
    role: Role!
}
enum Role { CUSTOMER ADMIN }
```

Drop `author` from `AddReviewInput`:

```graphql
input AddReviewInput {
    productId: ID!
    rating: Int!
    comment: String
}
```

In `DemoController.java`: replace the `products`, `product`, `addReview` methods, add a new `author` `@BatchMapping`, add a `userService` field/constructor param:

```java
private final ProductCatalogService productCatalogService;
private final ReviewService reviewService;
private final UserService userService;
private final BatchLoaderRegistry batchLoaderRegistry;
```

```java
@QueryMapping
public Connection<Product> products(@Argument ProductFilter filter, @Argument Integer first, @Argument String after) {
	Connection<Product> page = productCatalogService.listProducts(filter, first, after);
	log.info("[products] returning {} of {} total products", page.edges().size(), page.totalCount());
	return page;
}

@QueryMapping
public Product product(@Argument String id) {
	log.info("[product] looking up productId={}", id);
	FailureSimulator.maybeThrow("product query");
	return productCatalogService.findProduct(id).orElse(null);
}
```

```java
@MutationMapping
@PreAuthorize("isAuthenticated()")
public Review addReview(@Argument AddReviewInput input, Principal principal) {
	log.info("[addReview] productId={} username={} rating={}", input.productId(), principal.getName(), input.rating());
	Long authorId = userService.findByUsername(principal.getName())
			.orElseThrow(() -> new IllegalStateException("Authenticated principal has no matching User: " + principal.getName()))
			.id();
	return reviewService.addReview(input.productId(), authorId, input.rating(), input.comment());
}
```

```java
@BatchMapping
public Map<Review, User> author(List<Review> reviews) {
	Map<Long, User> byId = userService.findByIds(reviews.stream().map(Review::authorId).distinct().toList());
	Map<Review, User> result = new LinkedHashMap<>();
	for (Review review : reviews) {
		result.put(review, byId.get(review.authorId()));
	}
	return result;
}
```

Add the corresponding imports (`com.testingai.graphql.domain.User`, `com.testingai.graphql.domain.UserService`, `java.security.Principal`, `java.util.LinkedHashMap`, `java.util.Map`, `org.springframework.graphql.data.method.annotation.BatchMapping`).

Delete `.../controller/DemoControllerTest.java` entirely — it hand-constructed `DemoController` with in-memory `ProductCatalogService`/`ReviewService` instances; both are now DB-backed constructor dependencies (`ProductRepository`, `ReviewRepository`, `UserRepository`), so that construction pattern no longer compiles, and there is no remaining pure-POJO logic in `DemoController` worth a separate unit-test tier (every method now either delegates directly to a repository-backed service or requires a real Spring Security context for `@PreAuthorize`/`Principal`). Its coverage is fully superseded by `ProductCatalogServiceTest`/`ReviewServiceTest` (this task, Step 9) and `DemoIntegrationTest` (this task, Step 10).

- [ ] **Step 9: Rewrite `ProductCatalogServiceTest` and `ReviewServiceTest` against H2**

`.../domain/ProductCatalogServiceTest.java` (full replacement):

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductCatalogServiceTest {

	@Autowired
	private ProductRepository productRepository;

	private ProductCatalogService service;

	@BeforeEach
	void setUp() {
		service = new ProductCatalogService(productRepository);
		productRepository.save(newProduct("Mini Widget", 636, 10));
		productRepository.save(newProduct("Standard Widget", 2006, 10));
		productRepository.save(newProduct("Mini Gadget", 700, 10));
	}

	private static ProductEntity newProduct(String name, long priceCents, int stockQty) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(priceCents);
		entity.setStockQty(stockQty);
		return entity;
	}

	@Test
	void listProducts_returnsAllProducts_whenNoFilter() {
		Connection<Product> connection = service.listProducts(null, 50, null);

		assertThat(connection.totalCount()).isEqualTo(3);
		assertThat(connection.edges()).hasSize(3);
	}

	@Test
	void findProduct_returnsEmpty_whenIdIsNotNumeric() {
		assertThat(service.findProduct("not-a-number")).isEmpty();
	}

	@Test
	void findProduct_returnsEmpty_whenUnknown() {
		assertThat(service.findProduct("999999")).isEmpty();
	}

	@Test
	void listProducts_filtersByNameContains_caseInsensitive() {
		Connection<Product> connection = service.listProducts(new ProductFilter("mini", null, null), 50, null);

		assertThat(connection.edges()).extracting(edge -> edge.node().name())
				.allSatisfy(name -> assertThat(name.toLowerCase()).contains("mini"));
		assertThat(connection.totalCount()).isEqualTo(2);
	}

	@Test
	void listProducts_filtersByPriceRange() {
		Connection<Product> connection = service.listProducts(new ProductFilter(null, 1000, 3000), 50, null);

		assertThat(connection.edges()).extracting(edge -> edge.node().priceCents())
				.allSatisfy(priceCents -> assertThat(priceCents).isBetween(1000L, 3000L));
	}

	@Test
	void listProducts_pushesPaginationToTheDatabase_returningOnlyTheRequestedPage() {
		Connection<Product> firstPage = service.listProducts(null, 2, null);

		assertThat(firstPage.edges()).hasSize(2);
		assertThat(firstPage.pageInfo().hasNextPage()).isTrue();

		Connection<Product> secondPage = service.listProducts(null, 2, firstPage.pageInfo().endCursor());

		assertThat(secondPage.edges()).hasSize(1);
		assertThat(secondPage.pageInfo().hasNextPage()).isFalse();
	}

	@Test
	void findByIds_returnsMapKeyedByStringId() {
		ProductEntity saved = productRepository.save(newProduct("Gizmo", 400, 5));

		var byId = service.findByIds(List.of(saved.getId().toString()));

		assertThat(byId.get(saved.getId().toString()).name()).isEqualTo("Gizmo");
	}
}
```

(Add `import java.util.List;` alongside the others.)

`.../domain/ReviewServiceTest.java` (full replacement):

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewServiceTest {

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private UserRepository userRepository;

	private ReviewService service;
	private ProductEntity product;
	private UserEntity author;

	@BeforeEach
	void setUp() {
		service = new ReviewService(reviewRepository, productRepository, userRepository);

		product = new ProductEntity();
		product.setName("Widget");
		product.setPriceCents(999);
		product.setStockQty(10);
		product = productRepository.save(product);

		author = new UserEntity();
		author.setUsername("jordan");
		author.setEmail("jordan@example.com");
		author.setDisplayName("Jordan");
		author.setRole(Role.CUSTOMER);
		author = userRepository.save(author);
	}

	@Test
	void findByProductIds_batchesInOneCall() {
		Map<String, List<Review>> result = service.findByProductIds(List.of(product.getId().toString()));

		assertThat(result).containsOnlyKeys(product.getId().toString());
		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}

	@Test
	void findByProductIds_returnsEmptyList_forProductWithNoReviews() {
		Map<String, List<Review>> result = service.findByProductIds(List.of(product.getId().toString()));

		assertThat(result.get(product.getId().toString())).isEmpty();
	}

	@Test
	void addReview_storesReview_andEmitsToSink() {
		String productId = product.getId().toString();

		StepVerifier.create(service.reviewAdded())
				.then(() -> service.addReview(productId, author.getId(), 5, "Great product")).assertNext(review -> {
					assertThat(review.authorId()).isEqualTo(author.getId());
					assertThat(review.productId()).isEqualTo(productId);
				}).thenCancel().verify();

		assertThat(service.findByProductIds(List.of(productId)).get(productId))
				.anyMatch(review -> review.authorId().equals(author.getId()));
	}

	@Test
	void addReview_throws_whenProductUnknown() {
		assertThatThrownBy(() -> service.addReview("999999", author.getId(), 5, "x"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deleteReview_removesMatchingReview_andReturnsTrue() {
		Review review = service.addReview(product.getId().toString(), author.getId(), 5, "Great product");

		boolean deleted = service.deleteReview(review.id());

		assertThat(deleted).isTrue();
		assertThat(service.findByProductIds(List.of(product.getId().toString())).get(product.getId().toString()))
				.doesNotContain(review);
	}

	@Test
	void deleteReview_returnsFalse_whenReviewIdIsNotAValidUuid() {
		assertThat(service.deleteReview("not-a-uuid")).isFalse();
	}

	@Test
	void deleteReview_returnsFalse_whenReviewUnknown() {
		assertThat(service.deleteReview(java.util.UUID.randomUUID().toString())).isFalse();
	}

	@Test
	void findByProductIds_filtersByMinRating() {
		String productId = product.getId().toString();
		service.addReview(productId, author.getId(), 2, "meh");
		service.addReview(productId, author.getId(), 5, "great");

		List<Review> reviews = service.findByProductIds(List.of(productId), new ReviewFilter(4)).get(productId);

		assertThat(reviews).extracting(Review::rating).containsOnly(5);
	}

	@Test
	void findByProductIds_withFilter_stillBatchesInOneCall() {
		service.findByProductIds(List.of(product.getId().toString()), new ReviewFilter(3));

		assertThat(service.getBatchCallCount()).isEqualTo(1);
	}

}
```

- [ ] **Step 10: Rewrite the affected parts of `DemoIntegrationTest`**

The existing `40`/`p1`-hardcoded assertions no longer apply (there's no fixed seeded catalog until Task 5). Replace `DemoIntegrationTest`'s product/review-related tests with versions that create their own fixture data via `@Autowired ProductRepository`/`UserRepository` in `@BeforeEach`, matching the pattern established in Step 9's service tests. Concretely:

- Add `@Autowired private ProductRepository productRepository;` and `@Autowired private UserRepository userRepository;` fields.
- Add a `@BeforeEach void seedFixtureData()` that inserts a small, known set of products (e.g. 3, with distinct names/prices) and ensures `"user"`/`"admin"` `UserEntity` rows exist (matching `SecurityConfig`'s Basic-Auth accounts) so `addReview`'s principal-to-`User` lookup resolves.
- Replace every literal `"p1"`/`40`/`"Mini Widget"` reference with the fixture data's actual generated ids/names/counts.
- `mutation_addReview_succeeds_whenAuthenticatedAsUser`: drop `author: "Jordan"` from the mutation's `input` (no longer a valid field) and assert on `addReview.author.username` (now a nested `User`) instead of `addReview.author` (a string).
- `query_returnsProductsWithNestedReviews_batchedInOneCall`: query `reviews { edges { node { id author { username } rating } } }` instead of `author` as a scalar.
- `subscription_streamsReviewAdded_whenMutationPublishes`: same nested-`author` shape change in the subscription's selection set.

This step is intentionally described rather than fully re-transcribed line-by-line: apply the mechanical replacements above directly against the existing file read at the start of this plan (`communication-protocols/graphql/spring-demo/src/test/java/com/testingai/graphql/controller/DemoIntegrationTest.java`), preserving every test's existing assertions and structure except where the schema/fixture-data changes above require it.

- [ ] **Step 11: Run the full module test suite**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`.

- [ ] **Step 12: Commit**

```bash
git add communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): migrate Product/Review to Postgres with keyset pagination"
```

---

### Task 4: Order & OrderItem persistence + transactional `placeOrder`/`updateOrderStatus` logic

**Files:**
- Create: `.../db.changelog-6-create-orders-table.xml`
- Create: `.../db.changelog-7-create-order-items-table.xml`
- Modify: `.../db.changelog-master.xml`
- Create: `.../entity/OrderEntity.java`
- Create: `.../entity/OrderItemEntity.java`
- Create: `.../repository/OrderRepository.java`
- Create: `.../repository/OrderItemRepository.java`
- Create: `.../repository/OrderSpecifications.java`
- Create: `.../domain/OrderStatus.java`
- Create: `.../domain/Order.java`
- Create: `.../domain/OrderItem.java`
- Create: `.../domain/OrderItemInput.java`
- Create: `.../domain/PlaceOrderInput.java`
- Create: `.../exception/InsufficientStockException.java`
- Create: `.../domain/OrderService.java`
- Test: `.../domain/OrderServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (Task 2), `ProductRepository`/`ProductRepository.findByIdForUpdate` (Task 3).
- Produces: `Order(Long id, Long userId, OrderStatus status, String placedAt, long totalCents)`, `OrderItem(Long id, Long orderId, Long productId, int quantity, long unitPriceCents)` with a `lineTotalCents()` derived method, `OrderService.placeOrder(String username, List<OrderItemInput>): Order`, `OrderService.updateOrderStatus(Long, OrderStatus): Order` — both consumed by Task 7's mutation resolvers; Task 7 also extends this same `OrderService` class with query-side methods (`findByUserIds`, `findItemsByOrderIds`, `listOrders`, `findById`).

- [ ] **Step 1: Add the Liquibase changesets**

`.../db.changelog-6-create-orders-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="6-create-orders-table" author="migration">
        <createTable tableName="orders">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="user_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_orders_user" references="users(id)"/>
            </column>
            <column name="status" type="VARCHAR(20)">
                <constraints nullable="false"/>
            </column>
            <column name="placed_at" type="TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

`.../db.changelog-7-create-order-items-table.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="7-create-order-items-table" author="migration">
        <createTable tableName="order_items">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="order_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_order_items_order" references="orders(id)"/>
            </column>
            <column name="product_id" type="BIGINT">
                <constraints nullable="false" foreignKeyName="fk_order_items_product" references="products(id)"/>
            </column>
            <column name="quantity" type="INT">
                <constraints nullable="false"/>
            </column>
            <column name="unit_price_cents" type="BIGINT">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

Add both `<include>` lines to `db.changelog-master.xml`.

- [ ] **Step 2: Write `OrderStatus`, `OrderEntity`, `OrderItemEntity`, repositories**

`.../domain/OrderStatus.java`:

```java
package com.testingai.graphql.domain;

public enum OrderStatus {
	PENDING, PAID, SHIPPED, DELIVERED, CANCELLED
}
```

`.../entity/OrderEntity.java`:

```java
package com.testingai.graphql.entity;

import com.testingai.graphql.domain.OrderStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "orders")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@ToString.Exclude
	private List<OrderItemEntity> items = new ArrayList<>();
}
```

`.../entity/OrderItemEntity.java`:

```java
package com.testingai.graphql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	@ToString.Exclude
	private OrderEntity order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price_cents", nullable = false)
	private long unitPriceCents;
}
```

`.../repository/OrderRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
}
```

`.../repository/OrderItemRepository.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.OrderItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
	List<OrderItemEntity> findByOrderIdIn(List<Long> orderIds);
}
```

`.../repository/OrderSpecifications.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.domain.OrderStatus;
import com.testingai.graphql.entity.OrderEntity;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

	private OrderSpecifications() {
	}

	public static Specification<OrderEntity> matchingStatus(OrderStatus status) {
		return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
	}

	public static Specification<OrderEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}
}
```

- [ ] **Step 3: Write `Order`, `OrderItem`, the input records, and `InsufficientStockException`**

`.../domain/Order.java`:

```java
package com.testingai.graphql.domain;

public record Order(Long id, Long userId, OrderStatus status, String placedAt, long totalCents) {
}
```

`placedAt` is a `String` (not `Instant`) so it maps onto the schema's `placedAt: String!` without a custom GraphQL scalar — `OrderService.toOrder` formats it via `Instant.toString()` (ISO-8601), matching this demo's "no custom scalars" scope.

`.../domain/OrderItem.java`:

```java
package com.testingai.graphql.domain;

public record OrderItem(Long id, Long orderId, Long productId, int quantity, long unitPriceCents) {
	public long lineTotalCents() {
		return (long) quantity * unitPriceCents;
	}
}
```

`.../domain/OrderItemInput.java`:

```java
package com.testingai.graphql.domain;

public record OrderItemInput(String productId, int quantity) {
}
```

`.../domain/PlaceOrderInput.java`:

```java
package com.testingai.graphql.domain;

import java.util.List;

public record PlaceOrderInput(List<OrderItemInput> items) {
}
```

`.../exception/InsufficientStockException.java`:

```java
package com.testingai.graphql.exception;

/**
 * Extends {@link IllegalArgumentException} specifically so {@link com.testingai.graphql.exception.DemoExceptionResolver}'s
 * existing {@code instanceof IllegalArgumentException -> BAD_REQUEST} classification handles it with no resolver
 * changes — same reasoning as {@code addReview}'s "unknown product" check.
 */
public class InsufficientStockException extends IllegalArgumentException {
	public InsufficientStockException(String message) {
		super(message);
	}
}
```

- [ ] **Step 4: Write the failing `OrderServiceTest`**

`.../domain/OrderServiceTest.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.exception.InsufficientStockException;
import com.testingai.graphql.repository.OrderItemRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// NOT_SUPPORTED: @DataJpaTest normally wraps each test in a transaction it rolls back afterward, but that would
// make OrderService's own @Transactional just join that already-open transaction instead of getting its own — so
// a mid-method exception would only mark it rollback-only, and the "nothing was persisted" assertions below would
// still see the not-yet-rolled-back, in-flight changes within the SAME transaction. Disabling the wrapper transaction
// lets OrderService.placeOrder's @Transactional commit/rollback for real, independently, which is what these tests
// need to observe.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderServiceTest {

	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private OrderItemRepository orderItemRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private UserRepository userRepository;

	private OrderService orderService;
	private ProductEntity product;
	private UserEntity user;

	@BeforeEach
	void setUp() {
		orderService = new OrderService(orderRepository, orderItemRepository, productRepository, userRepository);

		user = new UserEntity();
		user.setUsername("jordan-" + System.nanoTime());
		user.setEmail("jordan@example.com");
		user.setDisplayName("Jordan");
		user.setRole(Role.CUSTOMER);
		user = userRepository.save(user);

		product = new ProductEntity();
		product.setName("Widget");
		product.setPriceCents(999);
		product.setStockQty(5);
		product = productRepository.save(product);
	}

	@Test
	void placeOrder_decrementsStock_andComputesTotal() {
		Order order = orderService.placeOrder(user.getUsername(), List.of(new OrderItemInput(product.getId().toString(), 2)));

		assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
		assertThat(order.totalCents()).isEqualTo(1998);
		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(3);
	}

	@Test
	void placeOrder_throwsInsufficientStock_andPersistsNothing_whenQuantityExceedsStock() {
		assertThatThrownBy(() -> orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 99))))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(orderRepository.findAll()).isEmpty();
		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(5);
	}

	@Test
	void placeOrder_rollsBackEarlierDecrements_whenALaterItemFails() {
		ProductEntity secondProduct = new ProductEntity();
		secondProduct.setName("Gadget");
		secondProduct.setPriceCents(500);
		secondProduct.setStockQty(1);
		secondProduct = productRepository.save(secondProduct);

		assertThatThrownBy(() -> orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 1),
						new OrderItemInput(secondProduct.getId().toString(), 99))))
				.isInstanceOf(InsufficientStockException.class);

		assertThat(productRepository.findById(product.getId())).get().extracting(ProductEntity::getStockQty)
				.isEqualTo(5);
		assertThat(orderRepository.findAll()).isEmpty();
	}

	@Test
	void placeOrder_throws_whenUserUnknown() {
		assertThatThrownBy(() -> orderService.placeOrder("nobody-" + System.nanoTime(), List.of()))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	void updateOrderStatus_changesStatus() {
		Order placed = orderService.placeOrder(user.getUsername(),
				List.of(new OrderItemInput(product.getId().toString(), 1)));

		Order updated = orderService.updateOrderStatus(placed.id(), OrderStatus.SHIPPED);

		assertThat(updated.status()).isEqualTo(OrderStatus.SHIPPED);
	}
}
```

Each test generates a unique username (`"jordan-" + System.nanoTime()`) because `@Transactional(NOT_SUPPORTED)` means rows really do commit and persist across test methods within the class (no per-test rollback) — the emptiness/count assertions above are all scoped to that test's own freshly-created user/product rather than the whole table, so cross-test data doesn't affect them.

- [ ] **Step 5: Run it, verify it fails to compile, then write `OrderService`**

`.../domain/OrderService.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.OrderEntity;
import com.testingai.graphql.entity.OrderItemEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.exception.InsufficientStockException;
import com.testingai.graphql.repository.OrderItemRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;

	public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
			ProductRepository productRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Order placeOrder(String username, List<OrderItemInput> items) {
		UserEntity user = userRepository.findByUsername(username)
				.orElseThrow(() -> new NoSuchElementException("Unknown user: " + username));

		OrderEntity order = new OrderEntity();
		order.setUser(user);
		order.setStatus(OrderStatus.PENDING);
		order.setPlacedAt(Instant.now());

		for (OrderItemInput itemInput : items) {
			Long productId = Long.parseLong(itemInput.productId());
			// Pessimistic write lock: two concurrent placeOrder calls against the same product must not both read
			// the same stockQty and both succeed a decrement that oversells it.
			ProductEntity product = productRepository.findByIdForUpdate(productId)
					.orElseThrow(() -> new IllegalArgumentException("Unknown product: " + itemInput.productId()));
			if (product.getStockQty() < itemInput.quantity()) {
				throw new InsufficientStockException("Insufficient stock for product " + itemInput.productId()
						+ ": requested " + itemInput.quantity() + ", available " + product.getStockQty());
			}
			product.setStockQty(product.getStockQty() - itemInput.quantity());

			OrderItemEntity item = new OrderItemEntity();
			item.setOrder(order);
			item.setProduct(product);
			item.setQuantity(itemInput.quantity());
			item.setUnitPriceCents(product.getPriceCents());
			order.getItems().add(item);
		}

		return toOrder(orderRepository.save(order));
	}

	@Transactional
	public Order updateOrderStatus(Long orderId, OrderStatus status) {
		OrderEntity order = orderRepository.findById(orderId)
				.orElseThrow(() -> new NoSuchElementException("Unknown order: " + orderId));
		order.setStatus(status);
		return toOrder(order);
	}

	static Order toOrder(OrderEntity entity) {
		long totalCents = entity.getItems().stream().mapToLong(i -> (long) i.getQuantity() * i.getUnitPriceCents()).sum();
		return new Order(entity.getId(), entity.getUser().getId(), entity.getStatus(), entity.getPlacedAt().toString(),
				totalCents);
	}
}
```

- [ ] **Step 6: Run `OrderServiceTest`**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=OrderServiceTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Run the full module test suite, then commit**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`.

```bash
git add communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): add Order/OrderItem persistence and placeOrder business logic"
```

---

### Task 5: `DemoDataSeeder` (configurable-volume, deterministic seeding)

**Files:**
- Create: `.../config/SeedProperties.java`
- Create: `.../config/DemoDataSeeder.java`
- Modify: `.../GraphQlSpringDemoApplication.java`
- Test: `.../config/DemoDataSeederTest.java`

**Interfaces:**
- Consumes: all six repositories (Tasks 2–4).
- Produces: a populated database on every app/test startup (idempotent — a no-op if `users` is already non-empty), including `UserEntity` rows for `"user"`/`"admin"` matching `SecurityConfig`'s Basic-Auth accounts.

- [ ] **Step 1: Write `SeedProperties` and enable it**

`.../config/SeedProperties.java`:

```java
package com.testingai.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean enabled, int userCount, int categoryCount, int productCount,
		int minReviewsPerProduct, int maxReviewsPerProduct, int orderCount) {
}
```

Modify `.../GraphQlSpringDemoApplication.java`:

```java
package com.testingai.graphql;

import com.testingai.graphql.config.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SeedProperties.class)
public class GraphQlSpringDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraphQlSpringDemoApplication.class, args);
	}
}
```

- [ ] **Step 2: Write the failing `DemoDataSeederTest`**

`.../config/DemoDataSeederTest.java`:

```java
package com.testingai.graphql.config;

import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoDataSeederTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ReviewRepository reviewRepository;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private SeedProperties seedProperties;
	@Autowired
	private DemoDataSeeder seeder;

	@Test
	void seededData_matchesConfiguredVolumes() {
		assertThat(userRepository.count()).isEqualTo(seedProperties.userCount());
		assertThat(categoryRepository.count()).isEqualTo(seedProperties.categoryCount());
		assertThat(productRepository.count()).isEqualTo(seedProperties.productCount());
		assertThat(orderRepository.count()).isEqualTo(seedProperties.orderCount());
	}

	@Test
	void seededUsers_includeTheTwoSecurityDemoAccounts() {
		assertThat(userRepository.findByUsername("user")).isPresent();
		assertThat(userRepository.findByUsername("admin")).isPresent();
	}

	@Test
	void everyProductsReviewTotal_fallsWithinTheConfiguredAggregateRange() {
		long productCount = productRepository.count();
		long totalReviews = reviewRepository.count();

		assertThat(totalReviews).isBetween(productCount * seedProperties.minReviewsPerProduct(),
				productCount * seedProperties.maxReviewsPerProduct());
	}

	@Test
	void rerunningSeeder_isNoOp_whenDataAlreadyPresent() {
		long usersBefore = userRepository.count();

		seeder.run(null);

		assertThat(userRepository.count()).isEqualTo(usersBefore);
	}
}
```

- [ ] **Step 3: Run it, verify it fails to compile, then write `DemoDataSeeder`**

`.../config/DemoDataSeeder.java`:

```java
package com.testingai.graphql.config;

import com.testingai.graphql.domain.OrderStatus;
import com.testingai.graphql.domain.Role;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.entity.OrderEntity;
import com.testingai.graphql.entity.OrderItemEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ReviewEntity;
import com.testingai.graphql.entity.UserEntity;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.OrderRepository;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ReviewRepository;
import com.testingai.graphql.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic (fixed-seed {@link Random}) demo data generator — volumes come from {@link SeedProperties}, not
 * hardcoded, so the test profile (small) and default profile (10k products) share this exact class. Guarded by
 * {@code userRepository.count() == 0} so re-running the app against an already-populated database is a no-op.
 */
@Slf4j
@Component
public class DemoDataSeeder implements ApplicationRunner {

	private static final long SEED = 42L;
	private static final List<String> PRODUCT_NAMES = List.of("Widget", "Gadget", "Gizmo", "Doohickey", "Thingamajig",
			"Contraption", "Doodad", "Whatsit", "Gizmotron", "Thingamabob");
	private static final List<String> PRODUCT_VARIANTS = List.of("Mini", "Standard", "Pro", "Max");
	private static final List<String> CATEGORY_NAMES = List.of("Electronics", "Audio", "Home", "Kitchen", "Outdoors",
			"Office", "Sports", "Toys", "Books", "Garden");

	private final UserRepository userRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final ReviewRepository reviewRepository;
	private final OrderRepository orderRepository;
	private final SeedProperties seedProperties;
	private final Random random = new Random(SEED);

	public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
			ProductRepository productRepository, ReviewRepository reviewRepository, OrderRepository orderRepository,
			SeedProperties seedProperties) {
		this.userRepository = userRepository;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.reviewRepository = reviewRepository;
		this.orderRepository = orderRepository;
		this.seedProperties = seedProperties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!seedProperties.enabled() || userRepository.count() > 0) {
			log.info("[seed] skipped: seeding disabled or data already present");
			return;
		}
		List<UserEntity> users = seedUsers();
		List<CategoryEntity> categories = seedCategories();
		List<ProductEntity> products = seedProducts(categories);
		seedReviews(products, users);
		seedOrders(users, products);
		log.info("[seed] complete: {} users, {} categories, {} products", users.size(), categories.size(),
				products.size());
	}

	private List<UserEntity> seedUsers() {
		List<UserEntity> users = new ArrayList<>();
		users.add(newUser("user", "user@example.com", "Demo User", Role.CUSTOMER));
		users.add(newUser("admin", "admin@example.com", "Demo Admin", Role.ADMIN));
		for (int i = users.size(); i < seedProperties.userCount(); i++) {
			users.add(newUser("customer" + i, "customer" + i + "@example.com", "Customer " + i, Role.CUSTOMER));
		}
		return userRepository.saveAll(users);
	}

	private static UserEntity newUser(String username, String email, String displayName, Role role) {
		UserEntity user = new UserEntity();
		user.setUsername(username);
		user.setEmail(email);
		user.setDisplayName(displayName);
		user.setRole(role);
		return user;
	}

	private List<CategoryEntity> seedCategories() {
		List<CategoryEntity> roots = new ArrayList<>();
		for (String name : CATEGORY_NAMES) {
			CategoryEntity root = new CategoryEntity();
			root.setName(name);
			roots.add(root);
		}
		roots = categoryRepository.saveAll(roots);

		List<CategoryEntity> children = new ArrayList<>();
		int remaining = seedProperties.categoryCount() - roots.size();
		for (int i = 0; i < Math.max(0, remaining); i++) {
			CategoryEntity parent = roots.get(random.nextInt(roots.size()));
			CategoryEntity child = new CategoryEntity();
			child.setName(parent.getName() + " " + (i + 1));
			child.setParent(parent);
			children.add(child);
		}
		children = categoryRepository.saveAll(children);

		List<CategoryEntity> all = new ArrayList<>(roots);
		all.addAll(children);
		return all;
	}

	private List<ProductEntity> seedProducts(List<CategoryEntity> categories) {
		List<ProductEntity> products = new ArrayList<>();
		for (int i = 0; i < seedProperties.productCount(); i++) {
			String variant = PRODUCT_VARIANTS.get(random.nextInt(PRODUCT_VARIANTS.size()));
			String name = PRODUCT_NAMES.get(random.nextInt(PRODUCT_NAMES.size()));
			ProductEntity product = new ProductEntity();
			product.setName(variant + " " + name + " #" + (i + 1));
			product.setPriceCents(499 + random.nextInt(9500));
			product.setStockQty(10 + random.nextInt(200));
			int categoryCount = 1 + random.nextInt(3);
			for (int c = 0; c < categoryCount; c++) {
				product.getCategories().add(categories.get(random.nextInt(categories.size())));
			}
			products.add(product);
		}
		return productRepository.saveAll(products);
	}

	private void seedReviews(List<ProductEntity> products, List<UserEntity> users) {
		List<ReviewEntity> reviews = new ArrayList<>();
		int span = seedProperties.maxReviewsPerProduct() - seedProperties.minReviewsPerProduct() + 1;
		for (ProductEntity product : products) {
			int count = seedProperties.minReviewsPerProduct() + (span > 0 ? random.nextInt(span) : 0);
			for (int i = 0; i < count; i++) {
				ReviewEntity review = new ReviewEntity();
				review.setProduct(product);
				review.setAuthor(users.get(random.nextInt(users.size())));
				review.setRating(1 + random.nextInt(5));
				review.setComment("Review #" + (i + 1) + " of " + product.getName());
				reviews.add(review);
			}
		}
		reviewRepository.saveAll(reviews);
	}

	private void seedOrders(List<UserEntity> users, List<ProductEntity> products) {
		List<OrderEntity> orders = new ArrayList<>();
		OrderStatus[] statuses = OrderStatus.values();
		for (int i = 0; i < seedProperties.orderCount(); i++) {
			OrderEntity order = new OrderEntity();
			order.setUser(users.get(random.nextInt(users.size())));
			order.setStatus(statuses[random.nextInt(statuses.length)]);
			order.setPlacedAt(Instant.now().minusSeconds(random.nextInt(60 * 60 * 24 * 90)));

			int lineCount = 1 + random.nextInt(5);
			for (int l = 0; l < lineCount; l++) {
				ProductEntity product = products.get(random.nextInt(products.size()));
				OrderItemEntity item = new OrderItemEntity();
				item.setOrder(order);
				item.setProduct(product);
				item.setQuantity(1 + random.nextInt(3));
				item.setUnitPriceCents(product.getPriceCents());
				order.getItems().add(item);
				product.setStockQty(Math.max(0, product.getStockQty() - item.getQuantity()));
			}
			orders.add(order);
		}
		orderRepository.saveAll(orders);
	}
}
```

- [ ] **Step 4: Run `DemoDataSeederTest`**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=DemoDataSeederTest`
Expected: `BUILD SUCCESS`. This will also be the first test run in which every *other* `@SpringBootTest`-based test class (e.g. `GraphQlSpringDemoApplicationTest`, `SecurityConfigTest`, `DemoIntegrationTest`) now boots against a fully-seeded (test-scale) database, since the seeder runs on every context startup.

- [ ] **Step 5: Run the full module test suite**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`. If `DemoIntegrationTest`'s fixture-seeding `@BeforeEach` (Task 3, Step 10) collides with the seeder's own `"user"`/`"admin"` rows (e.g. a duplicate-username insert), remove the now-redundant manual `"user"`/`"admin"` creation from that `@BeforeEach` — the seeder already guarantees both exist on every test context startup.

- [ ] **Step 6: Commit**

```bash
git add communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): add deterministic configurable-volume data seeder"
```

---

### Task 6: Category GraphQL exposure (schema, resolvers, DataLoaders)

**Files:**
- Create: `.../domain/CategoryService.java`
- Modify: `.../domain/ProductCatalogService.java` (add `findCategoriesByProductIds`, `listProductsInCategory`)
- Modify: `.../resources/graphql/schema.graphqls` (`Category`/`CategoryConnection`/`CategoryEdge`, `Product.categories`, `Query.categories`/`Query.category`)
- Modify: `.../controller/DemoController.java` (add `categories`/`category` queries, `Product.categories`/`Category.parent`/`Category.children`/`Category.products` resolvers, register the `categoryChildren` batch loader)
- Test: `.../domain/CategoryServiceTest.java`
- Modify: `.../controller/DemoIntegrationTest.java` (add Category coverage)

**Interfaces:**
- Consumes: `KeysetPagination`, `CursorPagination` (existing), `CategoryRepository`/`ProductRepository` (Tasks 2–3).
- Produces: `CategoryService.findCategory(Long): Optional<Category>`, `.listCategories(Integer, String): Connection<Category>`, `.findChildrenByParentIds(List<Long>): Map<Long, List<Category>>`, `.findByIds(List<Long>): Map<Long, Category>`, `CategoryService.toCategory(CategoryEntity): Category` (package-visible, used by `ProductCatalogService`).

- [ ] **Step 1: Write the failing `CategoryServiceTest`**

`.../domain/CategoryServiceTest.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryServiceTest {

	@Autowired
	private CategoryRepository categoryRepository;

	private CategoryService service;
	private CategoryEntity root;
	private CategoryEntity child;

	@BeforeEach
	void setUp() {
		service = new CategoryService(categoryRepository);

		root = new CategoryEntity();
		root.setName("Electronics");
		root = categoryRepository.save(root);

		child = new CategoryEntity();
		child.setName("Audio");
		child.setParent(root);
		child = categoryRepository.save(child);
	}

	@Test
	void findCategory_mapsParentIdFromNestedEntity() {
		Category found = service.findCategory(child.getId()).orElseThrow();

		assertThat(found.parentId()).isEqualTo(root.getId());
	}

	@Test
	void findCategory_hasNullParentId_forRootCategory() {
		Category found = service.findCategory(root.getId()).orElseThrow();

		assertThat(found.parentId()).isNull();
	}

	@Test
	void listCategories_pushesPaginationToTheDatabase() {
		Connection<Category> page = service.listCategories(1, null);

		assertThat(page.edges()).hasSize(1);
		assertThat(page.pageInfo().hasNextPage()).isTrue();
		assertThat(page.totalCount()).isEqualTo(2);
	}

	@Test
	void findChildrenByParentIds_returnsEmptyList_forParentWithNoChildren() {
		Map<Long, List<Category>> byParent = service.findChildrenByParentIds(List.of(root.getId(), child.getId()));

		assertThat(byParent.get(root.getId())).extracting(Category::name).containsExactly("Audio");
		assertThat(byParent.get(child.getId())).isEmpty();
	}

	@Test
	void findByIds_returnsMapKeyedById() {
		Map<Long, Category> byId = service.findByIds(List.of(root.getId()));

		assertThat(byId.get(root.getId()).name()).isEqualTo("Electronics");
	}
}
```

- [ ] **Step 2: Run it, verify it fails to compile, then write `CategoryService`**

`.../domain/CategoryService.java`:

```java
package com.testingai.graphql.domain;

import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.CategoryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public Optional<Category> findCategory(Long id) {
		return categoryRepository.findById(id).map(CategoryService::toCategory);
	}

	public Connection<Category> listCategories(Integer first, String after) {
		Long cursorId = KeysetPagination.decodeCursor(after);
		int limit = KeysetPagination.normalizeFirst(first);
		var spec = com.testingai.graphql.repository.CategorySpecifications.idAfter(cursorId);

		List<CategoryEntity> rows = categoryRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
				.getContent();
		return KeysetPagination.paginate(rows, limit, CategoryEntity::getId, CategoryService::toCategory,
				categoryRepository.count());
	}

	/** Batch-loads each category's full, unpaginated child list — cheap since only ~100 categories exist total. */
	public Map<Long, List<Category>> findChildrenByParentIds(List<Long> parentIds) {
		Map<Long, List<Category>> byParent = categoryRepository.findByParentIdIn(parentIds).stream()
				.map(CategoryService::toCategory).collect(Collectors.groupingBy(Category::parentId));
		Map<Long, List<Category>> result = new LinkedHashMap<>();
		for (Long parentId : parentIds) {
			result.put(parentId, byParent.getOrDefault(parentId, List.of()));
		}
		return result;
	}

	public Map<Long, Category> findByIds(List<Long> ids) {
		return categoryRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(CategoryEntity::getId, CategoryService::toCategory));
	}

	static Category toCategory(CategoryEntity entity) {
		Long parentId = entity.getParent() == null ? null : entity.getParent().getId();
		return new Category(entity.getId(), entity.getName(), parentId);
	}
}
```

This needs one more small file, `CategorySpecifications` (mirrors `ProductSpecifications`/`OrderSpecifications`'s `idAfter`):

`.../repository/CategorySpecifications.java`:

```java
package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

	private CategorySpecifications() {
	}

	public static Specification<CategoryEntity> idAfter(Long cursorId) {
		return (root, query, cb) -> cursorId == null ? cb.conjunction() : cb.greaterThan(root.get("id"), cursorId);
	}
}
```

Replace the fully-qualified `com.testingai.graphql.repository.CategorySpecifications.idAfter(cursorId)` reference in `CategoryService.listCategories` above with a proper `import com.testingai.graphql.repository.CategorySpecifications;` and the plain call `CategorySpecifications.idAfter(cursorId)`.

- [ ] **Step 3: Run `CategoryServiceTest`**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=CategoryServiceTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Add `findCategoriesByProductIds` and `listProductsInCategory` to `ProductCatalogService`**

Add these two methods to the existing `.../domain/ProductCatalogService.java` (from Task 3):

```java
public Connection<Product> listProductsInCategory(Long categoryId, ProductFilter filter, Integer first,
		String after) {
	Long cursorId = KeysetPagination.decodeCursor(after);
	int limit = KeysetPagination.normalizeFirst(first);
	var spec = ProductSpecifications.matching(filter).and(ProductSpecifications.idAfter(cursorId))
			.and(ProductSpecifications.inCategory(categoryId));
	var countSpec = ProductSpecifications.matching(filter).and(ProductSpecifications.inCategory(categoryId));

	List<ProductEntity> rows = productRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
			.getContent();
	return KeysetPagination.paginate(rows, limit, ProductEntity::getId, ProductCatalogService::toProduct,
			productRepository.count(countSpec));
}

public Map<String, List<Category>> findCategoriesByProductIds(List<String> productIds) {
	List<Long> ids = productIds.stream().map(Long::parseLong).toList();
	Map<String, List<Category>> result = new LinkedHashMap<>();
	for (ProductEntity entity : productRepository.findByIdInWithCategories(ids)) {
		result.put(entity.getId().toString(), entity.getCategories().stream().map(CategoryService::toCategory).toList());
	}
	for (String productId : productIds) {
		result.putIfAbsent(productId, List.of());
	}
	return result;
}
```

- [ ] **Step 5: Update `schema.graphqls`**

Add after `type Product { ... }`:

```graphql
type Category {
    id: ID!
    name: String!
    parent: Category
    children(first: Int, after: String): CategoryConnection!
    products(filter: ProductFilter, first: Int, after: String): ProductConnection!
}
type CategoryConnection {
    edges: [CategoryEdge!]!
    pageInfo: PageInfo!
    totalCount: Int!
}
type CategoryEdge {
    node: Category!
    cursor: String!
}
```

Add `categories: [Category!]!` to `type Product`:

```graphql
type Product {
    id: ID!
    name: String!
    priceCents: Int!
    stockQty: Int!
    categories: [Category!]!
    reviews(filter: ReviewFilter, first: Int, after: String): ReviewConnection!
}
```

Add to `type Query`:

```graphql
    categories(first: Int, after: String): CategoryConnection!
    category(id: ID!): Category
```

- [ ] **Step 6: Add the Category resolvers to `DemoController`**

Add a `categoryService` field/constructor param, then:

```java
@PostConstruct
void registerCategoryChildrenBatchLoader() {
	batchLoaderRegistry.<Long, List<Category>>forName("categoryChildren").registerMappedBatchLoader(
			(parentIds, environment) -> Mono.just(categoryService.findChildrenByParentIds(new ArrayList<>(parentIds))));
}

@QueryMapping
public Connection<Category> categories(@Argument Integer first, @Argument String after) {
	return categoryService.listCategories(first, after);
}

@QueryMapping
public Category category(@Argument Long id) {
	return categoryService.findCategory(id).orElse(null);
}

@BatchMapping(typeName = "Product", field = "categories")
public Map<Product, List<Category>> productCategories(List<Product> products) {
	Map<String, List<Category>> byProductId = productCatalogService
			.findCategoriesByProductIds(products.stream().map(Product::id).toList());
	Map<Product, List<Category>> result = new LinkedHashMap<>();
	for (Product product : products) {
		result.put(product, byProductId.getOrDefault(product.id(), List.of()));
	}
	return result;
}

@BatchMapping(typeName = "Category", field = "parent")
public Map<Category, Category> categoryParent(List<Category> categories) {
	List<Long> parentIds = categories.stream().map(Category::parentId).filter(java.util.Objects::nonNull).distinct()
			.toList();
	Map<Long, Category> byId = categoryService.findByIds(parentIds);
	Map<Category, Category> result = new LinkedHashMap<>();
	for (Category category : categories) {
		result.put(category, category.parentId() == null ? null : byId.get(category.parentId()));
	}
	return result;
}

@SchemaMapping(typeName = "Category", field = "children")
public CompletableFuture<Connection<Category>> categoryChildren(Category category, @Argument Integer first,
		@Argument String after, DataFetchingEnvironment environment) {
	DataLoader<Long, List<Category>> loader = environment.getDataLoaderRegistry().getDataLoader("categoryChildren");
	return loader.load(category.id()).thenApply(children -> CursorPagination.paginate(children, first, after));
}

@SchemaMapping(typeName = "Category", field = "products")
public Connection<Product> categoryProducts(Category category, @Argument ProductFilter filter, @Argument Integer first,
		@Argument String after) {
	return productCatalogService.listProductsInCategory(category.id(), filter, first, after);
}
```

`@BatchMapping(typeName = "Product", field = "categories")` uses explicit `typeName`/`field` (rather than relying on method-name inference) because a method literally named `categories` would otherwise collide, at the Java level, with the `categories(Integer, String)` `@QueryMapping` — explicit coordinates make both unambiguous to a reader even though Java's overload resolution alone would already disambiguate them by parameter types.

Add the corresponding imports: `com.testingai.graphql.domain.Category`, `com.testingai.graphql.domain.CategoryService`, `org.springframework.graphql.data.method.annotation.SchemaMapping` (if not already present).

- [ ] **Step 7: Add Category coverage to `DemoIntegrationTest`**

Add tests exercising: `categories(first: N) { edges { node { id name } } totalCount }`; a `category(id:)` query traversing `parent { name }` and `children { edges { node { name } } }`; `category(id:) { products(first: N) { totalCount } }`; and a `products { edges { node { categories { name } } } }` query asserting the `Product.categories` batch resolver runs once per query regardless of page size (same style as the existing `query_returnsProductsWithNestedReviews_batchedInOneCall` test, but there's no batch-call counter for categories to assert on — instead assert the returned data shape is correct for at least 2 products in the same response). Use the fixture data seeded by `DemoDataSeeder` (test-profile volumes: 10 categories, 50 products) rather than hand-rolling more fixtures, now that Task 5 guarantees a populated database on every test context startup.

- [ ] **Step 8: Run the full module test suite, then commit**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`.

```bash
git add communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): expose Category through GraphQL (tree + many-to-many)"
```

---

### Task 7: User/Order/OrderItem GraphQL exposure, row-level authorization, mutations

**Files:**
- Modify: `.../domain/OrderService.java` (add `findByUserIds`, `findItemsByOrderIds`, `listOrders`, `findById`)
- Modify: `.../resources/graphql/schema.graphqls` (extend `User` with `orders`, add `Order`/`OrderItem`/`OrderConnection`/`OrderEdge`/`OrderStatus`, `PlaceOrderInput`/`OrderItemInput`, `Query.me`/`order`/`orders`, `Mutation.placeOrder`/`updateOrderStatus`)
- Modify: `.../controller/DemoController.java` (add all Order/User resolvers, register `userOrders` batch loader)
- Modify: `.../controller/DemoIntegrationTest.java` (add User/Order/authorization coverage)

**Interfaces:**
- Consumes: `OrderService` (Task 4), `UserService` (Task 2), `ProductCatalogService.findByIds` (Task 3).
- Produces: fully wired `me`/`order`/`orders` queries and `placeOrder`/`updateOrderStatus` mutations with row-level and role-level authorization.

- [ ] **Step 1: Extend `OrderService` with the query-side methods**

Add to `.../domain/OrderService.java` (imports: `java.util.LinkedHashMap`, `java.util.Map`, `java.util.stream.Collectors`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Sort`, `com.testingai.graphql.pagination.KeysetPagination`, `com.testingai.graphql.repository.OrderSpecifications`, `com.testingai.graphql.entity.OrderItemEntity`):

```java
public Map<Long, List<Order>> findByUserIds(List<Long> userIds) {
	Map<Long, List<Order>> byUserId = orderRepository
			.findAll((root, query, cb) -> root.get("user").get("id").in(userIds)).stream()
			.collect(Collectors.groupingBy(o -> o.getUser().getId(), Collectors.mapping(OrderService::toOrder, Collectors.toList())));
	Map<Long, List<Order>> result = new LinkedHashMap<>();
	for (Long userId : userIds) {
		result.put(userId, byUserId.getOrDefault(userId, List.of()));
	}
	return result;
}

public Map<Long, List<OrderItem>> findItemsByOrderIds(List<Long> orderIds) {
	Map<Long, List<OrderItem>> byOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
			.map(OrderService::toOrderItem).collect(Collectors.groupingBy(OrderItem::orderId));
	Map<Long, List<OrderItem>> result = new LinkedHashMap<>();
	for (Long orderId : orderIds) {
		result.put(orderId, byOrderId.getOrDefault(orderId, List.of()));
	}
	return result;
}

public Connection<Order> listOrders(OrderStatus status, Integer first, String after) {
	Long cursorId = KeysetPagination.decodeCursor(after);
	int limit = KeysetPagination.normalizeFirst(first);
	var spec = OrderSpecifications.matchingStatus(status).and(OrderSpecifications.idAfter(cursorId));

	List<OrderEntity> rows = orderRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id"))).getContent();
	return KeysetPagination.paginate(rows, limit, OrderEntity::getId, OrderService::toOrder,
			orderRepository.count(OrderSpecifications.matchingStatus(status)));
}

public Optional<Order> findById(Long id) {
	return orderRepository.findById(id).map(OrderService::toOrder);
}

static OrderItem toOrderItem(OrderItemEntity entity) {
	return new OrderItem(entity.getId(), entity.getOrder().getId(), entity.getProduct().getId(), entity.getQuantity(),
			entity.getUnitPriceCents());
}
```

(Add `import com.testingai.graphql.pagination.Connection;` and `import java.util.Optional;` to the file's existing imports if not already present from Task 4.)

- [ ] **Step 2: Write the failing `OrderService` query-method tests**

Add to `.../domain/OrderServiceTest.java` (same `@Transactional(NOT_SUPPORTED)` class as Task 4):

```java
@Test
void findByUserIds_returnsOnlyThatUsersOrders() {
	orderService.placeOrder(user.getUsername(), List.of(new OrderItemInput(product.getId().toString(), 1)));

	Map<Long, List<Order>> byUserId = orderService.findByUserIds(List.of(user.getId()));

	assertThat(byUserId.get(user.getId())).hasSize(1);
}

@Test
void findItemsByOrderIds_returnsThatOrdersLineItems() {
	Order order = orderService.placeOrder(user.getUsername(),
			List.of(new OrderItemInput(product.getId().toString(), 2)));

	Map<Long, List<OrderItem>> byOrderId = orderService.findItemsByOrderIds(List.of(order.id()));

	assertThat(byOrderId.get(order.id())).extracting(OrderItem::quantity).containsExactly(2);
}

@Test
void listOrders_filtersByStatus() {
	Order order = orderService.placeOrder(user.getUsername(),
			List.of(new OrderItemInput(product.getId().toString(), 1)));
	orderService.updateOrderStatus(order.id(), OrderStatus.SHIPPED);

	var shipped = orderService.listOrders(OrderStatus.SHIPPED, 50, null);
	var pending = orderService.listOrders(OrderStatus.PENDING, 50, null);

	assertThat(shipped.edges()).extracting(edge -> edge.node().id()).contains(order.id());
	assertThat(pending.edges()).extracting(edge -> edge.node().id()).doesNotContain(order.id());
}

@Test
void findById_returnsOrder_whenExists() {
	Order placed = orderService.placeOrder(user.getUsername(),
			List.of(new OrderItemInput(product.getId().toString(), 1)));

	assertThat(orderService.findById(placed.id())).isPresent();
}
```

(Add `import java.util.Map;` if not already present.)

- [ ] **Step 3: Run `OrderServiceTest`, verify the new tests fail (methods don't exist yet — but Step 1 already added them, so this confirms Step 1's code compiles and behaves correctly)**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo -Dtest=OrderServiceTest`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Update `schema.graphqls`**

Extend the existing `type User` (added in Task 3) with `orders`:

```graphql
type User {
    id: ID!
    username: String!
    displayName: String!
    role: Role!
    orders(first: Int, after: String): OrderConnection!
}
```

Add after `type Category`-related blocks:

```graphql
type Order {
    id: ID!
    user: User!
    status: OrderStatus!
    placedAt: String!
    items: [OrderItem!]!
    totalCents: Int!
}
enum OrderStatus { PENDING PAID SHIPPED DELIVERED CANCELLED }
type OrderItem {
    id: ID!
    product: Product!
    quantity: Int!
    unitPriceCents: Int!
    lineTotalCents: Int!
}
type OrderConnection {
    edges: [OrderEdge!]!
    pageInfo: PageInfo!
    totalCount: Int!
}
type OrderEdge {
    node: Order!
    cursor: String!
}

input PlaceOrderInput { items: [OrderItemInput!]! }
input OrderItemInput { productId: ID!, quantity: Int! }
```

Add to `type Query`:

```graphql
    me: User!
    order(id: ID!): Order
    orders(status: OrderStatus, first: Int, after: String): OrderConnection!
```

Add to `type Mutation`:

```graphql
    placeOrder(input: PlaceOrderInput!): Order!
    updateOrderStatus(id: ID!, status: OrderStatus!): Order!
```

- [ ] **Step 5: Add the Order/User resolvers to `DemoController`**

Add an `orderService` field/constructor param, then:

```java
@PostConstruct
void registerUserOrdersBatchLoader() {
	batchLoaderRegistry.<Long, List<Order>>forName("userOrders").registerMappedBatchLoader(
			(userIds, environment) -> Mono.just(orderService.findByUserIds(new ArrayList<>(userIds))));
}

@QueryMapping
@PreAuthorize("isAuthenticated()")
public User me(Principal principal) {
	return userService.findByUsername(principal.getName())
			.orElseThrow(() -> new IllegalStateException("Authenticated principal has no matching User: " + principal.getName()));
}

@QueryMapping
@PreAuthorize("isAuthenticated()")
public Order order(@Argument Long id, Principal principal) {
	Order order = orderService.findById(id).orElse(null);
	if (order == null) {
		return null;
	}
	User caller = userService.findByUsername(principal.getName())
			.orElseThrow(() -> new IllegalStateException("Authenticated principal has no matching User: " + principal.getName()));
	boolean isOwner = order.userId().equals(caller.id());
	boolean isAdmin = caller.role() == Role.ADMIN;
	if (!isOwner && !isAdmin) {
		throw new AccessDeniedException("Not authorized to view order " + id);
	}
	return order;
}

@QueryMapping
@PreAuthorize("hasRole('ADMIN')")
public Connection<Order> orders(@Argument OrderStatus status, @Argument Integer first, @Argument String after) {
	return orderService.listOrders(status, first, after);
}

@MutationMapping
@PreAuthorize("isAuthenticated()")
public Order placeOrder(@Argument PlaceOrderInput input, Principal principal) {
	log.info("[placeOrder] username={} itemCount={}", principal.getName(), input.items().size());
	return orderService.placeOrder(principal.getName(), input.items());
}

@MutationMapping
@PreAuthorize("hasRole('ADMIN')")
public Order updateOrderStatus(@Argument Long id, @Argument OrderStatus status) {
	log.info("[updateOrderStatus] orderId={} status={}", id, status);
	return orderService.updateOrderStatus(id, status);
}

@BatchMapping(typeName = "Order", field = "user")
public Map<Order, User> orderUser(List<Order> orders) {
	Map<Long, User> byId = userService.findByIds(orders.stream().map(Order::userId).distinct().toList());
	Map<Order, User> result = new LinkedHashMap<>();
	for (Order order : orders) {
		result.put(order, byId.get(order.userId()));
	}
	return result;
}

@BatchMapping(typeName = "OrderItem", field = "product")
public Map<OrderItem, Product> orderItemProduct(List<OrderItem> orderItems) {
	Map<String, Product> byId = productCatalogService
			.findByIds(orderItems.stream().map(item -> item.productId().toString()).distinct().toList());
	Map<OrderItem, Product> result = new LinkedHashMap<>();
	for (OrderItem item : orderItems) {
		result.put(item, byId.get(item.productId().toString()));
	}
	return result;
}

@BatchMapping(typeName = "Order", field = "items")
public Map<Order, List<OrderItem>> orderItems(List<Order> orders) {
	Map<Long, List<OrderItem>> byOrderId = orderService.findItemsByOrderIds(orders.stream().map(Order::id).toList());
	Map<Order, List<OrderItem>> result = new LinkedHashMap<>();
	for (Order order : orders) {
		result.put(order, byOrderId.getOrDefault(order.id(), List.of()));
	}
	return result;
}

@SchemaMapping(typeName = "User", field = "orders")
public CompletableFuture<Connection<Order>> userOrders(User user, @Argument Integer first, @Argument String after,
		DataFetchingEnvironment environment) {
	DataLoader<Long, List<Order>> loader = environment.getDataLoaderRegistry().getDataLoader("userOrders");
	return loader.load(user.id()).thenApply(orders -> CursorPagination.paginate(orders, first, after));
}
```

Add imports: `com.testingai.graphql.domain.Order`, `com.testingai.graphql.domain.OrderItem`, `com.testingai.graphql.domain.OrderItemInput`, `com.testingai.graphql.domain.OrderService`, `com.testingai.graphql.domain.OrderStatus`, `com.testingai.graphql.domain.PlaceOrderInput`, `com.testingai.graphql.domain.Role`, `org.springframework.security.access.AccessDeniedException`.

- [ ] **Step 6: Add User/Order/authorization coverage to `DemoIntegrationTest`**

Add tests covering, following the existing file's `asUser()`/`asAdmin()` helper conventions:

- `me { username role }` as user vs. as admin, returning the right `role`.
- `placeOrder` as an authenticated user with a valid product/quantity: asserts `status: PENDING`, `totalCents` matches `quantity * priceCents`, and `items { product { id } quantity }` round-trips.
- `placeOrder` with a quantity exceeding a product's `stockQty`: asserts a `BAD_REQUEST`-classified error (same assertion style as `mutation_addReview_isRejected_whenProductUnknown`).
- `placeOrder` as anonymous: `UNAUTHORIZED`.
- `order(id:)` as the owning user: succeeds. As a *different* authenticated user: `FORBIDDEN`. As admin: succeeds regardless of ownership.
- `orders(status: PENDING)` as a non-admin user: `FORBIDDEN`. As admin: succeeds.
- `updateOrderStatus` as admin: succeeds, returns the new `status`. As a non-admin user: `FORBIDDEN`.
- `me { orders(first: 5) { totalCount } }` traversal, using an order placed earlier in the same test.

- [ ] **Step 7: Run the full module test suite**

Run: `cd communication-protocols && mvn test -pl graphql/spring-demo`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add communication-protocols/graphql/spring-demo
git commit -m "feat(communication-protocols): expose User/Order/OrderItem with row-level authorization"
```

---

### Task 8: Documentation

**Files:**
- Modify: `communication-protocols/graphql/spring-demo/README.md`
- Modify: `communication-protocols/graphql/README.md`

**Interfaces:**
- Consumes: nothing (documentation only).
- Produces: nothing consumed by later tasks — this is the last task.

- [ ] **Step 1: Update `spring-demo/README.md`'s Prerequisites and Run sections**

In the `## Prerequisites` section, add a line noting Postgres is now required to run the app (but not to test it):

```markdown
- Docker (for Postgres — `docker compose -f ../docker/docker-compose.yml up -d` from this directory, or `docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d` from the repo root). Not required for `mvn test`, which runs against an embedded H2 database in Postgres-compatibility mode.
```

In the `## Run` section, add the `docker compose up -d` line immediately before the existing `mvn spring-boot:run` line, consistent with the `CLAUDE.md` change from Task 1.

- [ ] **Step 2: Add a "Domain model" section**

Insert a new `## Domain model` section immediately after the existing `## Security` section (before `## Build & test`):

```markdown
## Domain model

Six entities, Postgres-backed via Liquibase + Spring Data JPA (`entity`/`repository` packages), mapped to plain
GraphQL-facing records (`domain` package) exactly the way `Product`/`Review` already worked before this domain
extension:

- **User** — `username`/`displayName`/`role` (`CUSTOMER`/`ADMIN`); `username` matches the Basic-Auth demo accounts
  (`user`/`admin`) so the authenticated principal resolves directly to a domain `User`.
- **Category** — a self-referencing tree (`parent`/`children`) with a many-to-many relation to `Product`.
- **Product** — gains `stockQty` and `categories` alongside the existing `name`/`priceCents`.
- **Review** — `author` is now a full `User` (was a free-text string).
- **Order** / **OrderItem** — `placeOrder` creates both in one transaction; `OrderItem.unitPriceCents` is a snapshot
  of the product's price at order time, not a live join, so historical orders don't change value if a price changes
  later.

Seeded on every startup (idempotent — skipped if data already exists): 100 users, 100 categories, 10,000 products,
3–10 reviews per product, ~3,000 orders by default (`app.seed.*` in `application.yml`; much smaller in the test
profile so `mvn test` stays fast).

### Pagination: two strategies, chosen by scale

`products`, `Category.products`, and the admin `orders` query push pagination down to the database (keyset: cursor
encodes the last-seen row id, `WHERE id > :cursorId ORDER BY id LIMIT :n`) — these can legitimately span the whole
10k-row table. `Category.children`, `Product.reviews`, and `User.orders` keep the original in-memory
`CursorPagination` (full list loaded per parent, sliced afterward) — each parent's list is inherently small (at most
tens of rows) regardless of overall table size, so there's nothing to gain from pushing those down too.

### `@BatchMapping` vs. manual `DataLoader`

| Field | Mechanism | Why |
|---|---|---|
| `Product.categories`, `Category.parent`, `Review.author`, `Order.user`, `OrderItem.product`, `Order.items` | `@BatchMapping` | No `@Argument` needed — Spring GraphQL can batch these with zero manual registration |
| `Product.reviews`, `Category.children`, `User.orders` | Manual `BatchLoaderRegistry` | Need `@Argument` (filter/pagination), which `@BatchMapping` methods can't accept |
| `Category.products` | Neither — a direct per-node query | Would need to load a category's *entire* unpaginated product list per key just to slice it afterward, defeating the DB-pushdown pagination above |

### Row-level authorization

`order(id:)` is the one place in this demo where authorization depends on the data, not just the caller's role: the
resolver loads the order, then allows it only if the caller is the owning user or an `ADMIN`, throwing
`AccessDeniedException` (classified `FORBIDDEN`/`UNAUTHORIZED` by the existing `DemoExceptionResolver`, no changes
needed there) otherwise. Try it as two different `user`-role accounts against the same order id to see the
distinction from `deleteReview`'s plain role check.
```

- [ ] **Step 3: Update the top-level `communication-protocols/graphql/README.md`**

Read the existing file first (`communication-protocols/graphql/README.md`) and add one paragraph summarizing the domain extension and linking to `spring-demo/README.md`'s new "Domain model" section, matching that file's existing structure and tone (it was not fully reproduced in this plan — apply the addition directly against the file's current content).

- [ ] **Step 4: Commit**

```bash
git add communication-protocols/graphql/README.md communication-protocols/graphql/spring-demo/README.md
git commit -m "docs(communication-protocols): document the graphql demo's e-commerce domain extension"
```
