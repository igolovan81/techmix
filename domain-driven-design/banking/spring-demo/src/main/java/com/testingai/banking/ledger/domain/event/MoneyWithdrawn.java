package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.time.Instant;

public record MoneyWithdrawn(AccountId accountId, Money amount, Instant occurredAt) implements LedgerEvent {
}
