package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OpenAccountUseCase {

	private final AccountRepository accountRepository;
	private final ApplicationEventPublisher eventPublisher;

	public OpenAccountUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
		this.accountRepository = accountRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Account open(String ownerName, Money initialBalance) {
		log.info("[OpenAccountUseCase] Opening account owner={} initialBalance={}", ownerName, initialBalance);
		Account account = Account.open(ownerName, initialBalance);
		Account saved = accountRepository.save(account);
		account.pullDomainEvents().forEach(eventPublisher::publishEvent);
		log.info("[OpenAccountUseCase] Account opened id={}", saved.id().value());
		return saved;
	}
}
