package com.testingai.banking.statements.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.AccountOpened;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import com.testingai.banking.ledger.domain.event.MoneyWithdrawn;
import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementLineType;
import com.testingai.banking.statements.domain.StatementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerEventTranslatorTest {

	private final List<StatementLine> savedLines = new ArrayList<>();
	private final StatementRepository fakeRepository = new StatementRepository() {
		@Override
		public void save(StatementLine line) {
			savedLines.add(line);
		}

		@Override
		public List<StatementLine> findByAccountId(String accountId) {
			return savedLines;
		}
	};
	private final LedgerEventTranslator translator = new LedgerEventTranslator(fakeRepository);

	@Test
	void translatesAccountOpenedToCreditLine() {
		AccountId accountId = AccountId.newId();
		var event = new AccountOpened(accountId, "Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"),
				Instant.now());

		StatementLine line = translator.translate(event);

		assertThat(line.accountId()).isEqualTo(accountId.value().toString());
		assertThat(line.type()).isEqualTo(StatementLineType.CREDIT);
		assertThat(line.amount()).isEqualByComparingTo("100.00");
		assertThat(line.description()).isEqualTo("Account opened");
	}

	@Test
	void translatesMoneyDepositedToCreditLine() {
		AccountId accountId = AccountId.newId();
		var event = new MoneyDeposited(accountId, Money.of(new BigDecimal("25.00"), "USD"), Instant.now());

		StatementLine line = translator.translate(event);

		assertThat(line.type()).isEqualTo(StatementLineType.CREDIT);
		assertThat(line.description()).isEqualTo("Deposit");
	}

	@Test
	void translatesMoneyWithdrawnToDebitLine() {
		AccountId accountId = AccountId.newId();
		var event = new MoneyWithdrawn(accountId, Money.of(new BigDecimal("15.00"), "USD"), Instant.now());

		StatementLine line = translator.translate(event);

		assertThat(line.type()).isEqualTo(StatementLineType.DEBIT);
		assertThat(line.description()).isEqualTo("Withdrawal");
	}

	@Test
	void onLedgerEventSavesTranslatedLineToRepository() {
		AccountId accountId = AccountId.newId();
		var event = new MoneyDeposited(accountId, Money.of(new BigDecimal("25.00"), "USD"), Instant.now());

		translator.onLedgerEvent(event);

		assertThat(savedLines).hasSize(1);
		assertThat(savedLines.get(0).description()).isEqualTo("Deposit");
	}
}
