package com.testingai.surveyimporter.client;

public class RetryableSyncException extends RuntimeException {

	public RetryableSyncException(String message, Throwable cause) {
		super(message, cause);
	}
}
