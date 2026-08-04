package com.testingai.banking.ledger.domain;

import java.util.UUID;

public record AccountId(UUID value) {

	public static AccountId newId() {
		return new AccountId(UUID.randomUUID());
	}
}
