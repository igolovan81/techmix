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
