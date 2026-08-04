package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) {

	public Money {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
	}

	public static Money of(BigDecimal amount, String currencyCode) {
		return new Money(amount, Currency.getInstance(currencyCode));
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(this.amount.add(other.amount), this.currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(this.amount.subtract(other.amount), this.currency);
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isLessThan(Money other) {
		requireSameCurrency(other);
		return this.amount.compareTo(other.amount) < 0;
	}

	private void requireSameCurrency(Money other) {
		if (!this.currency.equals(other.currency)) {
			throw new CurrencyMismatchException("Cannot combine %s and %s".formatted(this.currency.getCurrencyCode(),
					other.currency.getCurrencyCode()));
		}
	}
}
