package com.testingai.hibernate.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class IsbnConverter implements AttributeConverter<Isbn, String> {

	@Override
	public String convertToDatabaseColumn(Isbn isbn) {
		return isbn == null ? null : isbn.value();
	}

	@Override
	public Isbn convertToEntityAttribute(String dbData) {
		return dbData == null ? null : new Isbn(dbData);
	}
}
