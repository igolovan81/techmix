package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.ledger.domain.exception.InsufficientFundsException;
import com.testingai.banking.ledger.domain.exception.InvalidAmountException;
import java.time.Instant;

public final class Account extends AggregateRoot {

	private final AccountId id;
	private final String ownerName;
	private Money balance;

	private Account(AccountId id, String ownerName, Money balance) {
		this.id = id;
		this.ownerName = ownerName;
		this.balance = balance;
	}

	public static Account open(String ownerName, Money initialBalance) {
		if (initialBalance.isNegative()) {
			throw new InvalidAmountException("Initial balance must not be negative");
		}
		AccountId id = AccountId.newId();
		Account account = new Account(id, ownerName, initialBalance);
		account.registerEvent(new AccountOpened(id, ownerName, initialBalance, Instant.now()));
		return account;
	}

	public static Account reconstitute(AccountId id, String ownerName, Money balance) {
		return new Account(id, ownerName, balance);
	}

	public void deposit(Money amount) {
		requirePositive(amount);
		this.balance = this.balance.plus(amount);
		registerEvent(new MoneyDeposited(id, amount, Instant.now()));
	}

	public void withdraw(Money amount) {
		requirePositive(amount);
		if (balance.isLessThan(amount)) {
			throw new InsufficientFundsException(
					"Account %s has insufficient funds for withdrawal of %s".formatted(id.value(), amount.amount()));
		}
		this.balance = this.balance.minus(amount);
		registerEvent(new MoneyWithdrawn(id, amount, Instant.now()));
	}

	private void requirePositive(Money amount) {
		if (amount.amount().signum() <= 0) {
			throw new InvalidAmountException("Amount must be positive: " + amount.amount());
		}
	}

	public AccountId id() {
		return id;
	}

	public String ownerName() {
		return ownerName;
	}

	public Money balance() {
		return balance;
	}
}
