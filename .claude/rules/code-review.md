# Code Review Rules

## FailureSimulator consistency

All `FailureSimulator` utility classes across `message-brokers/` modules must follow the pattern established in the Kafka module (`message-brokers/kafka/spring-demo/src/main/java/com/testingai/kafka/util/FailureSimulator.java`):

- Use a named constant `FAILURE_RATE = 0.05` (5%)
- Expose `maybeThrow(String context)` that throws `RuntimeException` on failure
- Do **not** use a `shouldFail()` method returning `boolean`
