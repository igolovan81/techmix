package com.testingai.hibernate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of {@code HibernateDemoApplication} so that {@code @WebMvcTest} slices (which use the
 * {@code @SpringBootConfiguration}-annotated application class as their context root) don't pull in
 * {@code JpaAuditingHandler} — that bean requires a non-empty JPA metamodel, which a web-only slice
 * never registers.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
