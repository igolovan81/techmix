package com.testingai.banking.statements.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class StatementPersistenceTest {

	@Autowired
	private SpringDataStatementRepository springDataStatementRepository;

	@Test
	void savesAndFindsStatementLinesByAccountId() {
		String accountId = UUID.randomUUID().toString();
		StatementLine line = new StatementLine(UUID.randomUUID(), accountId, StatementLineType.CREDIT,
				new BigDecimal("100.00"), "USD", "Account opened", Instant.now());

		springDataStatementRepository.save(StatementLineMapper.toEntity(line));

		var found = springDataStatementRepository.findByAccountIdOrderByOccurredAtAsc(accountId);
		assertThat(found).hasSize(1);
		assertThat(found.get(0).getDescription()).isEqualTo("Account opened");
	}
}
