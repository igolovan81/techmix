package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "statement_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StatementLineJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatementLineType type;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currencyCode;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private Instant occurredAt;
}
