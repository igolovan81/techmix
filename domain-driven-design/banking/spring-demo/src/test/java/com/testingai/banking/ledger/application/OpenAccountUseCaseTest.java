package com.testingai.banking.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.event.AccountOpened;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class OpenAccountUseCaseTest {

	private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
	private final List<Object> publishedEvents = new ArrayList<>();
	private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
	private final OpenAccountUseCase useCase = new OpenAccountUseCase(repository, eventPublisher);

	@Test
	void opensAccountAndPublishesAccountOpenedEvent() {
		var account = useCase.open("Ada Lovelace", Money.of(new BigDecimal("100.00"), "USD"));

		assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
		assertThat(repository.findById(account.id())).isPresent();
		assertThat(publishedEvents).hasSize(1);
		assertThat(publishedEvents.get(0)).isInstanceOf(AccountOpened.class);
	}
}
