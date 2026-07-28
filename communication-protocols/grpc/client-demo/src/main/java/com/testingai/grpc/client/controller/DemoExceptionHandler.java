package com.testingai.grpc.client.controller;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionHandler {

	@ExceptionHandler(StatusRuntimeException.class)
	public ResponseEntity<String> handleGrpcError(StatusRuntimeException exception) {
		HttpStatus httpStatus = switch (exception.getStatus().getCode()) {
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case INTERNAL -> HttpStatus.BAD_GATEWAY;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
		return ResponseEntity.status(httpStatus)
				.body(exception.getStatus().getCode() + ": " + exception.getStatus().getDescription());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleUnexpectedException(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
	}
}
