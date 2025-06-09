package com.testingai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestingAiGenerationApplication {

  private static final Logger logger = LoggerFactory.getLogger(TestingAiGenerationApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(TestingAiGenerationApplication.class, args);

    logger.info("Testing AI generation application started successfully!");
    logger.debug("This is a debug message for AI generation testing");
    logger.warn("This is a warning message for AI generation testing");

    // Demonstrate different log levels
    for (int i = 1; i <= 5; i++) {
      logger.info("Processing AI generation test iteration: {}", i);

      if (i == 3) {
        logger.warn("Reached iteration 3 - testing AI generation capabilities");
      }

      if (i == 5) {
        logger.debug("Final AI generation test iteration completed");
      }
    }

    // Example of logging with exception
    try {
      // Simulate some operation that might fail
      if (Math.random() > 0.5) {
        throw new RuntimeException("Simulated error for AI generation testing");
      }
      logger.info("AI generation test operation completed successfully");
    } catch (Exception e) {
      logger.error("An error occurred during AI generation testing", e);
    }

    logger.info("Testing AI generation application execution completed");
  }
}