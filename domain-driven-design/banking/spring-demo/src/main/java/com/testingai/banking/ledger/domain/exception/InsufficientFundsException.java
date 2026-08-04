package com.testingai.banking.ledger.domain.exception;

public class InsufficientFundsException extends DomainException {

	public InsufficientFundsException(String message) {
		super(message);
	}
}
