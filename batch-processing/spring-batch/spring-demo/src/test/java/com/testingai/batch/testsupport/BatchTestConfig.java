package com.testingai.batch.testsupport;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal bootstrap for job-config integration tests: {@code @SpringBootConfiguration} +
 * {@code @EnableAutoConfiguration} (DataSource, Batch infrastructure, schema init) without {@code @ComponentScan}, so
 * including this alongside one job's own beans in {@code @SpringBootTest(classes = ...)} doesn't drag in the other four
 * Job beans.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class BatchTestConfig {
}
