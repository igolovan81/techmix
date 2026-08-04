package com.testingai.banking.statements.infrastructure.acl;

import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.LedgerEvent;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LedgerEventTranslator {

	private final StatementRepository statementRepository;

	public LedgerEventTranslator(StatementRepository statementRepository) {
		this.statementRepository = statementRepository;
	}

	@EventListener
	public void onLedgerEvent(LedgerEvent event) {
		statementRepository.save(translate(event));
	}

	StatementLine translate(LedgerEvent event) {
		return switch (event) {
			case AccountOpened(var accountId, var ownerName, var openingBalance, var occurredAt) -> new StatementLine(
					UUID.randomUUID(), accountId.value().toString(), StatementLineType.CREDIT, openingBalance.amount(),
					openingBalance.currency().getCurrencyCode(), "Account opened", occurredAt);
			case MoneyDeposited(var accountId, var amount, var occurredAt) ->
				new StatementLine(UUID.randomUUID(), accountId.value().toString(), StatementLineType.CREDIT,
						amount.amount(), amount.currency().getCurrencyCode(), "Deposit", occurredAt);
			case MoneyWithdrawn(var accountId, var amount, var occurredAt) ->
				new StatementLine(UUID.randomUUID(), accountId.value().toString(), StatementLineType.DEBIT,
						amount.amount(), amount.currency().getCurrencyCode(), "Withdrawal", occurredAt);
		};
	}
}
