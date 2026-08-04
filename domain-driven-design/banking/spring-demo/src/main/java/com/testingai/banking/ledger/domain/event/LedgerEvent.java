package com.testingai.banking.ledger.domain.event;

import com.testingai.banking.ledger.domain.AccountId;
import java.time.Instant;

public sealed interface LedgerEvent permits AccountOpened, MoneyDeposited, MoneyWithdrawn {
	AccountId accountId();

	Instant occurredAt();
}
