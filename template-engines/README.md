# Template Engines — Demos

This directory contains runnable demos for Java template-rendering libraries, structured the same way as `../noSQL/`: one Spring Boot demo app per technology, no external infrastructure required.

| Engine | Demo | Best fit |
|---|---|---|
| [Handlebars](handlebars/) | `com.github.jknack:handlebars` | Logic-less, Mustache-compatible templates; helpers/partials as the only escape hatch |
| [FreeMarker](freemarker/) | `spring-boot-starter-freemarker` | Full-featured templating language: macros, functions, directives, built-ins |

More template engines may be added here over time, at which point this README will grow into a comparison guide like `../message-brokers/README.md`.
