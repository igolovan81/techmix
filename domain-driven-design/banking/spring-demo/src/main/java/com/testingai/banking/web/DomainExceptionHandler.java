package com.testingai.banking.web;

import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import com.testingai.banking.ledger.domain.exception.DomainException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class DomainExceptionHandler {

	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(AccountNotFoundException exception) {
		log.warn("[DomainExceptionHandler] Account not found: {}", exception.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "ACCOUNT_NOT_FOUND", "message", exception.getMessage()));
	}

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(DomainException exception) {
		log.warn("[DomainExceptionHandler] {}: {}", exception.getClass().getSimpleName(), exception.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", exception.getClass().getSimpleName(), "message", exception.getMessage()));
	}
}
