package com.testingai.banking.web;

import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import com.testingai.banking.ledger.domain.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DomainExceptionHandler {

	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(AccountNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "ACCOUNT_NOT_FOUND", "message", exception.getMessage()));
	}

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(DomainException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", exception.getClass().getSimpleName(), "message", exception.getMessage()));
	}
}
