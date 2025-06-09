package com.techmix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TechMixApplication {

  private static final Logger logger = LoggerFactory.getLogger(TechMixApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(TechMixApplication.class, args);

    logger.info("TechMix Backend API started successfully!");
    logger.debug("This is a debug message");
    logger.warn("This is a warning message");

    // Demonstrate different log levels
    for (int i = 1; i <= 5; i++) {
      logger.info("Processing iteration: {}", i);

      if (i == 3) {
        logger.warn("Reached iteration 3 - this might be important");
      }

      if (i == 5) {
        logger.debug("Final iteration completed");
      }
    }

    // Example of logging with exception
    try {
      // Simulate some operation that might fail
      if (Math.random() > 0.5) {
        throw new RuntimeException("Simulated error for logging demonstration");
      }
      logger.info("Operation completed successfully");
    } catch (Exception e) {
      logger.error("An error occurred during operation", e);
    }

    logger.info("TechMix Backend API execution completed");
  }
}