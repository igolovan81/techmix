package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import com.testingai.banking.ledger.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

	@Test
	void openingAnAccountRegistersAccountOpenedEvent() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
		assertThat(account.balance().amount()).isEqualByComparingTo("100.00");
		assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(AccountOpened.class);
	}

	@Test
	void openingWithNegativeInitialBalanceIsRejected() {
		assertThatThrownBy(() -> Account.open("Ada Lovelace", Money.of(new BigDecimal("-1.00"), "USD")))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void depositIncreasesBalanceAndRegistersMoneyDeposited() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		account.pullDomainEvents();

		account.deposit(Money.of(new BigDecimal("50.00"), "USD"));

		assertThat(account.balance().amount()).isEqualByComparingTo("150.00");
		assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(MoneyDeposited.class);
	}

	@Test
	void depositRejectsNonPositiveAmount() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		assertThatThrownBy(() -> account.deposit(Money.of(BigDecimal.ZERO, "USD")))
				.isInstanceOf(InvalidAmountException.class);
	}

	@Test
	void depositRejectsMismatchedCurrency() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		assertThatThrownBy(() -> account.deposit(Money.of(new BigDecimal("10.00"), "EUR")))
				.isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	void withdrawDecreasesBalanceAndRegistersMoneyWithdrawn() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));
		account.pullDomainEvents();

		account.withdraw(Money.of(new BigDecimal("40.00"), "USD"));

		assertThat(account.balance().amount()).isEqualByComparingTo("60.00");
		assertThat(account.pullDomainEvents()).hasSize(1).first().isInstanceOf(MoneyWithdrawn.class);
	}

	@Test
	void withdrawBeyondBalanceIsRejected() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("10.00"), "USD"));

		assertThatThrownBy(() -> account.withdraw(Money.of(new BigDecimal("20.00"), "USD")))
				.isInstanceOf(InsufficientFundsException.class);
		assertThat(account.balance().amount()).isEqualByComparingTo("10.00");
	}

	@Test
	void withdrawRejectsNonPositiveAmount() {
		Account account = Account.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		assertThatThrownBy(() -> account.withdraw(Money.of(BigDecimal.ZERO, "USD")))
				.isInstanceOf(InvalidAmountException.class);
	}
}
