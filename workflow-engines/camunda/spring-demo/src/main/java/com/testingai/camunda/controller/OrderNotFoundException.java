package com.testingai.camunda.controller;

public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException(String orderId) {
		super("Unknown order: " + orderId);
	}
}
