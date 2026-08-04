package com.testingai.banking.statements.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementLine(UUID id, String accountId, StatementLineType type, BigDecimal amount, String currencyCode,
		String description, Instant occurredAt) {
}
