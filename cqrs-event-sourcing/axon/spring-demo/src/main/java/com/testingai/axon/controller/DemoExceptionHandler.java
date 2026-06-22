package com.testingai.axon.controller;

import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(CommandExecutionException.class)
	public ResponseEntity<String> handleCommandExecutionException(CommandExecutionException exception) {
		HttpStatus status = exception.getDetails().isPresent() ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(exception.getMessage());
	}
}
