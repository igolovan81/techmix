package com.testingai.graphql.exception;

/**
 * Extends {@link IllegalArgumentException} specifically so {@link DemoExceptionResolver}'s existing
 * {@code instanceof IllegalArgumentException -> BAD_REQUEST} classification handles it with no resolver changes — same
 * reasoning as {@code addReview}'s "unknown product" check.
 */
public class InsufficientStockException extends IllegalArgumentException {
	public InsufficientStockException(String message) {
		super(message);
	}
}
