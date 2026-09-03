package com.testingai.hibernate.locking;

public class CopyAlreadyLoanedException extends RuntimeException {

	public CopyAlreadyLoanedException(Long copyId) {
		super("Copy already on loan: " + copyId);
	}
}
