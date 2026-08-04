package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.TransferService;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TransferMoneyUseCaseTest {

	private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
	private final List<Object> publishedEvents = new ArrayList<>();
	private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
	private final TransferMoneyUseCase useCase = new TransferMoneyUseCase(repository, new TransferService(),
			eventPublisher);

	@Test
	void transfersBetweenTwoAccountsAndPublishesBothEvents() {
		Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		source.pullDomainEvents();
		Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
		target.pullDomainEvents();
		repository.save(source);
		repository.save(target);

		useCase.transfer(source.id(), target.id(), Money.of(new BigDecimal("30.00"), "USD"));

		assertThat(repository.findById(source.id()).orElseThrow().balance().amount()).isEqualByComparingTo("70.00");
		assertThat(repository.findById(target.id()).orElseThrow().balance().amount()).isEqualByComparingTo("40.00");
		assertThat(publishedEvents).hasSize(2);
	}

	@Test
	void doesNotCreditTargetWhenSourceHasInsufficientFunds() {
		Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("5.00"), "USD"));
		source.pullDomainEvents();
		Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
		target.pullDomainEvents();
		repository.save(source);
		repository.save(target);

		assertThatThrownBy(() -> useCase.transfer(source.id(), target.id(), Money.of(new BigDecimal("30.00"), "USD")))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(repository.findById(target.id()).orElseThrow().balance().amount()).isEqualByComparingTo("10.00");
		assertThat(publishedEvents).isEmpty();
	}
}
