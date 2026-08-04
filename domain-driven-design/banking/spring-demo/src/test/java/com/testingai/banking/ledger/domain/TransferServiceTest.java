package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

	private final TransferService transferService = new TransferService();

	@Test
	void transferWithdrawsFromSourceAndDepositsToTarget() {
		Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
		source.pullDomainEvents();
		target.pullDomainEvents();

		transferService.transfer(source, target, Money.of(new BigDecimal("30.00"), "USD"));

		assertThat(source.balance().amount()).isEqualByComparingTo("70.00");
		assertThat(target.balance().amount()).isEqualByComparingTo("40.00");
		assertThat(source.pullDomainEvents()).hasSize(1);
		assertThat(target.pullDomainEvents()).hasSize(1);
	}

	@Test
	void transferLeavesTargetUntouchedWhenSourceHasInsufficientFunds() {
		Account source = Account.open("Ada Lovelace", Money.of(new BigDecimal("5.00"), "USD"));
		Account target = Account.open("Alan Turing", Money.of(new BigDecimal("10.00"), "USD"));
		source.pullDomainEvents();
		target.pullDomainEvents();

		assertThatThrownBy(() -> transferService.transfer(source, target, Money.of(new BigDecimal("30.00"), "USD")))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(target.balance().amount()).isEqualByComparingTo("10.00");
		assertThat(target.pullDomainEvents()).isEmpty();
	}
}
