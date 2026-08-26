package com.testingai.surveyimporter.client;

public class PermanentSyncException extends RuntimeException {

	public PermanentSyncException(String message) {
		super(message);
	}

	public PermanentSyncException(String message, Throwable cause) {
		super(message, cause);
	}
}
