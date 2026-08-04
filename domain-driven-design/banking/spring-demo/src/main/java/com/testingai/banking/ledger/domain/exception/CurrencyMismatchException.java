package com.testingai.banking.ledger.domain.exception;

public class CurrencyMismatchException extends DomainException {

	public CurrencyMismatchException(String message) {
		super(message);
	}
}
