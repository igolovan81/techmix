package com.testingai.banking.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccountJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String ownerName;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal balanceAmount;

	@Column(nullable = false, length = 3)
	private String balanceCurrency;
}
