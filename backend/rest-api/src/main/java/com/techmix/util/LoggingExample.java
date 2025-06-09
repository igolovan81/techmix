package com.techmix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Utility class demonstrating various logging patterns and best practices for TechMix application
 */
public class LoggingExample {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingExample.class);
    
    /**
     * Demonstrates basic logging levels
     */
    public void demonstrateLogLevels() {
        logger.trace("This is a TRACE message - most detailed");
        logger.debug("This is a DEBUG message - detailed information");
        logger.info("This is an INFO message - general information");
        logger.warn("This is a WARN message - potentially harmful situations");
        logger.error("This is an ERROR message - error events");
    }
    
    /**
     * Demonstrates parameterized logging for better performance
     */
    public void demonstrateParameterizedLogging(String username, int userId) {
        // Good practice - parameterized logging
        logger.info("User {} with ID {} logged in successfully", username, userId);
        
        // Avoid string concatenation in log messages
        // logger.info("User " + username + " with ID " + userId + " logged in successfully");
    }
    
    /**
     * Demonstrates logging with exceptions
     */
    public void demonstrateExceptionLogging() {
        try {
            // Simulate some operation that might fail
            throw new IllegalArgumentException("Invalid input provided");
        } catch (Exception e) {
            // Log the exception with stack trace
            logger.error("Failed to process request", e);
            
            // You can also log just the message without stack trace
            logger.warn("Operation failed: {}", e.getMessage());
        }
    }
    
    /**
     * Demonstrates Mapped Diagnostic Context (MDC) for contextual logging
     */
    public void demonstrateMDC(String requestId, String userId) {
        try {
            // Add context to MDC
            MDC.put("requestId", requestId);
            MDC.put("userId", userId);
            
            logger.info("Processing user request");
            logger.debug("Validating input parameters");
            logger.info("Request processed successfully");
            
        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
    
    /**
     * Demonstrates conditional logging for expensive operations
     */
    public void demonstrateConditionalLogging() {
        // For expensive operations, check if logging level is enabled
        if (logger.isDebugEnabled()) {
            String expensiveDebugInfo = generateExpensiveDebugInfo();
            logger.debug("Debug info: {}", expensiveDebugInfo);
        }
    }
    
    /**
     * Demonstrates structured logging with markers
     */
    public void demonstrateStructuredLogging() {
        // You can use markers for structured logging
        org.slf4j.Marker securityMarker = org.slf4j.MarkerFactory.getMarker("SECURITY");
        logger.warn(securityMarker, "Failed login attempt for user: {}", "john_doe");
        
        org.slf4j.Marker performanceMarker = org.slf4j.MarkerFactory.getMarker("PERFORMANCE");
        logger.info(performanceMarker, "Operation completed in {} ms", 150);
    }
    
    private String generateExpensiveDebugInfo() {
        // Simulate expensive operation
        return "Expensive debug information that takes time to generate";
    }
}