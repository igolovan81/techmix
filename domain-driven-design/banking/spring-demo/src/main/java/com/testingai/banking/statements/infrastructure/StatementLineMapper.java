package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLine;

public final class StatementLineMapper {

	private StatementLineMapper() {
	}

	public static StatementLineJpaEntity toEntity(StatementLine line) {
		return new StatementLineJpaEntity(line.id(), line.accountId(), line.type(), line.amount(), line.currencyCode(),
				line.description(), line.occurredAt());
	}

	public static StatementLine toDomain(StatementLineJpaEntity entity) {
		return new StatementLine(entity.getId(), entity.getAccountId(), entity.getType(), entity.getAmount(),
				entity.getCurrencyCode(), entity.getDescription(), entity.getOccurredAt());
	}
}
