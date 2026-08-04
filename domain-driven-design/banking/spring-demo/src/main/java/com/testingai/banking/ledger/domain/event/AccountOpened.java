package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.time.Instant;

public record AccountOpened(AccountId accountId, String ownerName, Money openingBalance,
		Instant occurredAt) implements LedgerEvent {
}
