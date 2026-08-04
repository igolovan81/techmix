package com.testingai.banking.ledger.domain;

import com.testingai.banking.ledger.domain.event.LedgerEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {

	private final List<LedgerEvent> pendingEvents = new ArrayList<>();

	protected void registerEvent(LedgerEvent event) {
		pendingEvents.add(event);
	}

	public List<LedgerEvent> pullDomainEvents() {
		List<LedgerEvent> events = List.copyOf(pendingEvents);
		pendingEvents.clear();
		return events;
	}
}
