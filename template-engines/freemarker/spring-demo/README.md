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

- http://localhost:8088/pages/products
- http://localhost:8088/pages/orders/o1
- http://localhost:8088/pages/orders/o2 (demonstrates the `!"pending"` default-value operator, since `o2` has no status)

## Capability endpoints

```bash
# Data-model binding: same rendering logic against a record vs. a Map
curl http://localhost:8088/demo/data-model

# #if / #list directives
curl http://localhost:8088/demo/directives/if-list

# #switch / #case / #default
curl http://localhost:8088/demo/directives/switch

# User-defined macro
curl http://localhost:8088/demo/macros

# User-defined function
curl http://localhost:8088/demo/functions

# Built-ins: ?upper_case, ?string number/date formatting
curl http://localhost:8088/demo/builtins

# #import composition, reusing the same layout.ftlh macro the MVC pages use
curl http://localhost:8088/demo/composition

# Null-safety operators: ! (default) and ?? (exists)
curl http://localhost:8088/demo/null-safety
```

## Swagger UI

http://localhost:8088/swagger-ui/index.html

## Run performance tests

```bash
mvn gatling:test
```

Requires the app to already be running in a separate terminal.
