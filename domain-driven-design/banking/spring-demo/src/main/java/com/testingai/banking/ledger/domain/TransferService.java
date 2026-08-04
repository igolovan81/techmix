package com.testingai.banking.ledger.domain;

public class TransferService {

	public void transfer(Account source, Account target, Money amount) {
		source.withdraw(amount);
		target.deposit(amount);
	}
}
