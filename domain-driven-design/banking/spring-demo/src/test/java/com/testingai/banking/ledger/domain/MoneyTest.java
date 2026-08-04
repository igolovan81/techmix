package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void plusAddsAmountsOfSameCurrency() {
		Money five = Money.of(new BigDecimal("5.00"), "USD");
		Money three = Money.of(new BigDecimal("3.00"), "USD");

		assertThat(five.plus(three).amount()).isEqualByComparingTo("8.00");
	}

	@Test
	void minusSubtractsAmountsOfSameCurrency() {
		Money five = Money.of(new BigDecimal("5.00"), "USD");
		Money three = Money.of(new BigDecimal("3.00"), "USD");

		assertThat(five.minus(three).amount()).isEqualByComparingTo("2.00");
	}

	@Test
	void plusRejectsMismatchedCurrencies() {
		Money usd = Money.of(new BigDecimal("5.00"), "USD");
		Money eur = Money.of(new BigDecimal("5.00"), "EUR");

		assertThatThrownBy(() -> usd.plus(eur)).isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	void isLessThanComparesSameCurrencyAmounts() {
		Money five = Money.of(new BigDecimal("5.00"), "USD");
		Money three = Money.of(new BigDecimal("3.00"), "USD");

		assertThat(three.isLessThan(five)).isTrue();
		assertThat(five.isLessThan(three)).isFalse();
	}

	@Test
	void isNegativeDetectsNegativeAmount() {
		assertThat(Money.of(new BigDecimal("-1.00"), "USD").isNegative()).isTrue();
		assertThat(Money.of(new BigDecimal("1.00"), "USD").isNegative()).isFalse();
	}
}
