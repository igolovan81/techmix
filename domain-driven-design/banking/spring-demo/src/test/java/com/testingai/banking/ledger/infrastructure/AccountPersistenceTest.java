package com.testingai.banking.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AccountPersistenceTest {

	@Autowired
	private SpringDataAccountRepository springDataAccountRepository;

	@Test
	void savesAndReloadsAccountPreservingMoneyAndIdentity() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		springDataAccountRepository.save(AccountMapper.toEntity(account));
		AccountJpaEntity reloaded = springDataAccountRepository.findById(account.id().value()).orElseThrow();
		Account reconstituted = AccountMapper.toDomain(reloaded);

		assertThat(reconstituted.id()).isEqualTo(account.id());
		assertThat(reconstituted.ownerName()).isEqualTo("Ada Lovelace");
		assertThat(reconstituted.balance().amount()).isEqualByComparingTo("100.00");
		assertThat(reconstituted.balance().currency().getCurrencyCode()).isEqualTo("USD");
	}
}
