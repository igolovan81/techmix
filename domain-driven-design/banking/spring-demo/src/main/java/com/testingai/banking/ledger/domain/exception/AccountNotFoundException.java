package com.testingai.banking.ledger.domain.exception;

public class AccountNotFoundException extends DomainException {

	public AccountNotFoundException(String message) {
		super(message);
	}
}
