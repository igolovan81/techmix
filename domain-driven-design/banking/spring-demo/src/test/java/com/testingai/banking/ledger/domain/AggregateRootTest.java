package com.testingai.banking.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.event.LedgerEvent;
import com.testingai.banking.ledger.domain.event.MoneyDeposited;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

	private static class TestAggregate extends AggregateRoot {
		void raise(LedgerEvent event) {
			registerEvent(event);
		}
	}

	@Test
	void pullDomainEventsReturnsAndClearsPendingEvents() {
		TestAggregate aggregate = new TestAggregate();
		AccountId accountId = AccountId.newId();
		MoneyDeposited event = new MoneyDeposited(accountId, Money.of(new BigDecimal("10.00"), "USD"), Instant.now());

		aggregate.raise(event);
		var pulled = aggregate.pullDomainEvents();

		assertThat(pulled).containsExactly(event);
		assertThat(aggregate.pullDomainEvents()).isEmpty();
	}
}
