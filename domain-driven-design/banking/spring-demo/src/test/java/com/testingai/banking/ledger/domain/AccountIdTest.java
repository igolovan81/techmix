package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdTest {

	@Test
	void newIdGeneratesUniqueValues() {
		assertThat(AccountId.newId()).isNotEqualTo(AccountId.newId());
	}

	@Test
	void wrapsGivenUuid() {
		UUID uuid = UUID.randomUUID();
		assertThat(new AccountId(uuid).value()).isEqualTo(uuid);
	}
}
