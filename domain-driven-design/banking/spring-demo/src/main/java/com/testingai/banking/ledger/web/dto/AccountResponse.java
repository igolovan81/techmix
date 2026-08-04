package com.testingai.banking.ledger.web.dto;

import com.testingai.banking.ledger.domain.Account;
import java.math.BigDecimal;

public record AccountResponse(String accountId, String ownerName, BigDecimal balance, String currency) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.id().value().toString(), account.ownerName(), account.balance().amount(),
				account.balance().currency().getCurrencyCode());
	}
}
