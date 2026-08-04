package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class WithdrawUseCaseTest {

	private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
	private final List<Object> publishedEvents = new ArrayList<>();
	private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
	private final WithdrawUseCase useCase = new WithdrawUseCase(repository, eventPublisher);

	@Test
	void withdrawsFromExistingAccountAndPublishesEvent() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		account.pullDomainEvents();
		repository.save(account);

		Account updated = useCase.withdraw(account.id(), Money.of(new BigDecimal("40.00"), "USD"));

		assertThat(updated.balance().amount()).isEqualByComparingTo("60.00");
		assertThat(publishedEvents).hasSize(1);
		assertThat(publishedEvents.get(0)).isInstanceOf(MoneyWithdrawn.class);
	}

	@Test
	void rejectsWithdrawalBeyondBalance() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("10.00"), "USD"));
		account.pullDomainEvents();
		repository.save(account);

		assertThatThrownBy(() -> useCase.withdraw(account.id(), Money.of(new BigDecimal("20.00"), "USD")))
				.isInstanceOf(InsufficientFundsException.class);
		assertThat(publishedEvents).isEmpty();
	}
}
