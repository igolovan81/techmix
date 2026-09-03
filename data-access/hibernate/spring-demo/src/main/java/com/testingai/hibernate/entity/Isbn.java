package com.testingai.hibernate.entity;

public record Isbn(String value) {

	public Isbn {
		if (value == null || !value.matches("\\d{3}-\\d{10}")) {
			throw new IllegalArgumentException("ISBN must match ddd-dddddddddd: " + value);
		}
	}
}
