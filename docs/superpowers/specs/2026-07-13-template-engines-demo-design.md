# Template Engines Demo Design (Handlebars & FreeMarker)

**Date:** 2026-07-13
**Status:** Approved

## Overview

A new top-level `template-engines/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`), with two independent Spring Boot demo apps: `handlebars` and `freemarker`. Each demo combines a real Spring MVC view layer (the engine wired in as the actual `ViewResolver`, rendering browsable HTML pages) with a REST `DemoController` exposing one endpoint per framework capability, returning the rendered fragment in the response body — matching the "trigger endpoint per pattern" convention used by every other module in this repo. Both modules render the same `Product`/`Order` domain already established in `noSQL/mongodb` and `cqrs-event-sourcing/axon`, kept in memory with no external infrastructure (no database, no docker-compose) since both engines are pure rendering libraries.

## Repository structure

```
template-engines/
├── pom.xml                                          (new parent POM, mirrors noSQL/pom.xml — no infra deps)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (short index comparing the two engines)
├── handlebars/
│   └── spring-demo/
│       ├── pom.xml                                  (artifactId: handlebars-demo)
│       └── src/
│           ├── main/
│           │   ├── java/com/testingai/handlebars/
│           │   │   ├── HandlebarsDemoApplication.java
│           │   │   ├── config/
│           │   │   │   └── HandlebarsConfig.java      (HandlebarsViewResolver + custom helper registration)
│           │   │   ├── controller/
│           │   │   │   ├── PageController.java         (real MVC pages)
│           │   │   │   └── DemoController.java         (REST, one endpoint per capability)
│           │   │   ├── model/
│           │   │   │   ├── Product.java                (record)
│           │   │   │   ├── OrderItem.java              (record)
│           │   │   │   └── Order.java                  (record)
│           │   │   └── service/
│           │   │       └── SampleDataService.java       (in-memory seed data)
│           │   └── resources/
│           │       ├── templates/
│           │       │   ├── layout.hbs
│           │       │   ├── products.hbs
│           │       │   ├── order-detail.hbs
│           │       │   └── partials/
│           │       │       └── order-item.hbs
│           │       └── application.yml
│           └── test/
│               ├── java/com/testingai/handlebars/
│               │   ├── HandlebarsDemoApplicationTest.java
│               │   ├── controller/PageControllerTest.java
│               │   ├── controller/DemoControllerTest.java
│               │   └── performance/DemoSimulation.java
│               └── resources/application.yml
│       └── README.md                                (sibling to pom.xml/src)
└── freemarker/
    └── spring-demo/
        ├── pom.xml                                  (artifactId: freemarker-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/freemarker/
            │   │   ├── FreemarkerDemoApplication.java
            │   │   ├── config/
            │   │   │   └── FreemarkerConfig.java       (FreeMarkerConfigurer customization: shared variables, exception handler)
            │   │   ├── controller/
            │   │   │   ├── PageController.java
            │   │   │   └── DemoController.java
            │   │   ├── model/
            │   │   │   ├── Product.java
            │   │   │   ├── OrderItem.java
            │   │   │   └── Order.java
            │   │   └── service/
            │   │       └── SampleDataService.java
            │   └── resources/
            │       ├── templates/
            │       │   ├── layout.ftl
            │       │   ├── products.ftl
            │       │   ├── order-detail.ftl
            │       │   └── macros/
            │       │       └── product-row.ftl
            │       └── application.yml
            └── test/
                ├── java/com/testingai/freemarker/
                │   ├── FreemarkerDemoApplicationTest.java
                │   ├── controller/PageControllerTest.java
                │   ├── controller/DemoControllerTest.java
                │   └── performance/DemoSimulation.java
                └── resources/application.yml
        └── README.md                                (sibling to pom.xml/src)
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep from `^(message-brokers|noSQL|cqrs-event-sourcing)/.*\.java$` to also match `^template-engines/.*\.java$`, and add a matching `mvn spotless:apply` block run from `template-engines/`.
- **`CLAUDE.md`** — add a "Template engine demos" command section (mirroring the NoSQL section), a `template-engines/` row in the repository layout table, and a line noting no infrastructure/docker is required for this category.

## Domain model

Shared by both modules, seeded in memory at startup by `SampleDataService` (no persistence, no database — consistent with the "pure rendering, no I/O" scope of this category):

```java
public record Product(String id, String name, BigDecimal price, int stock) {}
public record OrderItem(String productId, String productName, int quantity, BigDecimal lineTotal) {}
public record Order(String id, String customer, List<OrderItem> items, BigDecimal total, String status, Instant placedAt) {}
```

`SampleDataService` seeds ~4 products and ~2 orders (one with a null/blank field, e.g. no `status`, used deliberately by the FreeMarker null-safety demo).

## Handlebars module (`template-engines/handlebars/spring-demo`)

**Library:** `com.github.jknack:handlebars` (core rendering engine) + `com.github.jknack:handlebars-springmvc` (registers `HandlebarsViewResolver` for the MVC page layer).

**Pages — `PageController`, real Spring MVC view resolution:**

| Route | Template | Demonstrates |
|---|---|---|
| `GET /pages/products` | `products.hbs` extends `layout.hbs` | partial-block/layout inheritance, `#each` |
| `GET /pages/orders/{id}` | `order-detail.hbs` | `#with`, `#if`/`#unless`, partial `order-item.hbs` |

**REST capability endpoints — `DemoController`, returns rendered HTML (`text/html`) or plain text body:**

| Endpoint | Capability |
|---|---|
| `GET /demo/variables` | variable substitution, HTML auto-escaping, and `{{{raw}}}` triple-stache to show escaping opt-out |
| `GET /demo/helpers/builtin` | built-in block helpers `#if` / `#unless` / `#each` / `#with` |
| `GET /demo/helpers/custom` | custom helper registered via `Handlebars.registerHelper` — a currency formatter |
| `GET /demo/partials` | reusable partial fragment (`order-item.hbs`) rendered standalone |
| `GET /demo/layout` | partial-block-based layout composition (`{{#block}}` / `{{#partial}}`) |
| `GET /demo/subexpressions` | nested helper call, e.g. `{{formatCurrency (multiply price qty)}}` |
| `GET /demo/precompiled` | precompiled `Template` object reused across renders vs. re-parsing per call; response includes elapsed-time measurements for both paths to make the difference visible |

**`HandlebarsConfig`** registers the `HandlebarsViewResolver` bean (prefix `classpath:/templates/`, suffix `.hbs`) and the custom `formatCurrency`/`multiply` helpers on a shared `Handlebars` instance also used directly by `DemoController` for the non-MVC endpoints.

## FreeMarker module (`template-engines/freemarker/spring-demo`)

**Library:** `spring-boot-starter-freemarker` — auto-configures `freemarker.template.Configuration` and `FreeMarkerViewResolver` for the MVC page layer; the same `Configuration` bean is reused directly by `DemoController` to render ad hoc templates from strings for the standalone capability endpoints.

**Pages — `PageController`, real Spring MVC view resolution:**

| Route | Template | Demonstrates |
|---|---|---|
| `GET /pages/products` | `products.ftl` imports `layout.ftl` via `<#import>` | `#import`/`#include` composition, `#list` |
| `GET /pages/orders/{id}` | `order-detail.ftl` | `#if`, macro-based line-item rendering (`macros/product-row.ftl`), null-safety operators against the deliberately sparse seed order |

**REST capability endpoints — `DemoController`, returns rendered output (`text/html` or plain text) body:**

| Endpoint | Capability |
|---|---|
| `GET /demo/data-model` | same template rendered once against a POJO/record root and once against a `Map` root, response shows both outputs side by side |
| `GET /demo/directives/if-list` | `#if` / `#list` iterating the product catalog |
| `GET /demo/directives/switch` | `#switch` / `#case` / `#default` on order status |
| `GET /demo/macros` | user-defined `<#macro>` (`productRow`) invoked over the catalog |
| `GET /demo/functions` | user-defined `<#function>` (a discount calculator) called inline inside a template expression |
| `GET /demo/builtins` | built-ins: `?upper_case`, `?string` for number formatting, date built-ins on `placedAt` |
| `GET /demo/composition` | `#include` / `#import` layout composition, rendered standalone (mirrors what the MVC page does, but returned as a fragment) |
| `GET /demo/null-safety` | `!` default-value operator and `??` exists operator evaluated against the seed order with a missing field |

**`FreemarkerConfig`** customizes the auto-configured `freemarker.template.Configuration` (e.g. `TemplateExceptionHandler.RETHROW` for dev-friendly stack traces instead of the Spring Boot default `HTML_DEBUG` handler bleeding into REST responses) and exposes a `Configuration`-backed helper bean that `DemoController` uses to compile ad hoc string templates for the standalone endpoints (`new Template("name", new StringReader(source), configuration)`).

## Testing

- **`PageControllerTest`** (both modules) — `MockMvc` via `@WebMvcTest`, asserting the resolved view renders expected HTML fragments (product names, order totals) — plain JUnit/MockMvc, not Spock, so `@WebMvcTest` applies cleanly here.
- **`DemoControllerTest`** (both modules) — `MockMvc`, asserting each capability endpoint's response body contains the expected rendered markup/values for that feature.
- **Gatling** — `src/test/java/.../performance/DemoSimulation.java` per module, exercising all page and capability endpoints; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude in `template-engines/pom.xml`, run explicitly via `mvn gatling:test`.
- No `util/FailureSimulator` in either module — both are pure in-memory rendering with no external call to fail against; introducing simulated failure would be artificial here, unlike the broker/DB/CQRS modules where it models real infrastructure flakiness.

## Ports

- `handlebars/spring-demo` → `8085`
- `freemarker/spring-demo` → `8086`

(Next free slots after `noSQL/mongodb`'s `8084`.)

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`handlebars-demo` dependencies:** `spring-boot-starter-web`, `com.github.jknack:handlebars`, `com.github.jknack:handlebars-springmvc`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test).

**`freemarker-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-freemarker`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test).

## README

`template-engines/handlebars/spring-demo/README.md` and `template-engines/freemarker/spring-demo/README.md` follow the same format as `noSQL/mongodb/README.md`: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), page URLs to open in a browser, full curl list for every `/demo/*` capability endpoint, Swagger UI link, Gatling instructions, capability-to-feature mapping table.

`template-engines/README.md` is a short index, analogous to `noSQL/README.md`, comparing the two engines (best fit, MVC integration style) and ready to grow if more template engines are added later (e.g. Thymeleaf, Mustache).

## Scope limits

- No database or persistence layer — both demos are pure rendering over in-memory sample data; persistence is already covered by `backend/rest-api` and `noSQL/mongodb`.
- No docker-compose / external infrastructure — neither engine has a server component.
- No `FailureSimulator` — nothing external to fail against in either module.
- Extended capabilities (Handlebars `helperMissing`, cache-strategy comparison, `SafeString`; FreeMarker custom `TemplateDirectiveModel`, `OutputFormat` auto-escaping, recursive macros) are deliberately out of scope — the core-set capability tables above are what's implemented.
- No email/PDF rendering use case, even though it's a classic template-engine application — out of scope to keep both modules focused on the framework mechanics themselves via the `Product`/`Order` domain already established elsewhere in the repo.
- `message-brokers/README.md`'s broker comparison table is not touched — neither engine is a message broker.
