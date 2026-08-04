package com.testingai.websockets.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NoNextStatusException extends RuntimeException {

	public NoNextStatusException(String orderId, OrderStatus currentStatus) {
		super("Order " + orderId + " has no next status from terminal state " + currentStatus);
	}
}
