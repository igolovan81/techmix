package com.testingai.banking.ledger.domain.exception;

public abstract class DomainException extends RuntimeException {

	protected DomainException(String message) {
		super(message);
	}
}
