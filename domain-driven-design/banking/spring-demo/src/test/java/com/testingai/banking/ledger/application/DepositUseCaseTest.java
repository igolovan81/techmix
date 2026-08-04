package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DepositUseCaseTest {

	private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
	private final List<Object> publishedEvents = new ArrayList<>();
	private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
	private final DepositUseCase useCase = new DepositUseCase(repository, eventPublisher);

	@Test
	void depositsIntoExistingAccountAndPublishesEvent() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		account.pullDomainEvents();
		repository.save(account);

		Account updated = useCase.deposit(account.id(), Money.of(new BigDecimal("50.00"), "USD"));

		assertThat(updated.balance().amount()).isEqualByComparingTo("150.00");
		assertThat(publishedEvents).hasSize(1);
		assertThat(publishedEvents.get(0)).isInstanceOf(MoneyDeposited.class);
	}
}
