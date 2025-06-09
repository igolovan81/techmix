# Logging Configuration

This project uses SLF4J with Logback for comprehensive logging support.

## Features

### 1. Multiple Log Levels
- **TRACE**: Most detailed information
- **DEBUG**: Detailed information for debugging
- **INFO**: General information about application flow
- **WARN**: Potentially harmful situations
- **ERROR**: Error events that might still allow the application to continue

### 2. Multiple Appenders
- **Console Appender**: Logs to console/terminal
- **File Appender**: Logs to rotating files in `logs/` directory
- **Async Appender**: Improves performance by logging asynchronously

### 3. Configuration Files

#### application.properties
Basic logging configuration including:
- Root log level: INFO
- Application package log level: DEBUG
- Console and file patterns
- Optional file logging settings

#### logback-spring.xml
Advanced Logback configuration with:
- Rolling file appender (10MB max size, 30 days retention)
- Async logging for better performance
- Structured log patterns with timestamps and thread information

### 4. Best Practices Demonstrated

#### Parameterized Logging
```java
// Good - uses parameterized logging
logger.info("User {} logged in with ID {}", username, userId);

// Avoid - string concatenation
logger.info("User " + username + " logged in with ID " + userId);
```

#### Exception Logging
```java
try {
    // some operation
} catch (Exception e) {
    logger.error("Operation failed", e); // Includes stack trace
}
```

#### Conditional Logging
```java
if (logger.isDebugEnabled()) {
    String expensiveInfo = generateExpensiveDebugInfo();
    logger.debug("Debug info: {}", expensiveInfo);
}
```

#### MDC (Mapped Diagnostic Context)
```java
MDC.put("requestId", requestId);
MDC.put("userId", userId);
logger.info("Processing request");
MDC.clear(); // Always clear to prevent memory leaks
```

## Usage

### Running the Application
```bash
cd backend/rest-api
mvn spring-boot:run
```

### Log Files
Logs are written to:
- Console output
- `logs/application.log` (current log file)
- `logs/application.YYYY-MM-DD.N.log` (archived log files)

### Changing Log Levels
You can change log levels at runtime by modifying `application.properties`:

```properties
# Set root level
logging.level.root=WARN

# Set specific package level
logging.level.org.example=DEBUG

# Set specific class level
logging.level.org.example.Main=TRACE
```

### Environment-Specific Configuration
You can create environment-specific configuration files:
- `application-dev.properties` for development
- `application-prod.properties` for production
- `application-test.properties` for testing

Activate with: `--spring.profiles.active=dev`

## Monitoring and Analysis

### Log Analysis
- Use tools like ELK Stack (Elasticsearch, Logstash, Kibana) for log analysis
- Grep commands for quick searches: `grep "ERROR" logs/application.log`
- Use structured logging with markers for better filtering

### Performance Considerations
- Async appenders are configured for better performance
- Use conditional logging for expensive debug operations
- Parameterized logging prevents unnecessary string concatenation

## Security Considerations
- Avoid logging sensitive information (passwords, tokens, PII)
- Use markers to identify security-related events
- Consider log sanitization for user inputs