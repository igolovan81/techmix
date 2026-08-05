package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DepositUseCase {

	private final AccountRepository accountRepository;
	private final ApplicationEventPublisher eventPublisher;

	public DepositUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
		this.accountRepository = accountRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Account deposit(AccountId accountId, Money amount) {
		log.info("[DepositUseCase] Depositing accountId={} amount={}", accountId.value(), amount);
		Account account = accountRepository.findById(accountId).orElseThrow(() -> {
			log.warn("[DepositUseCase] Account not found id={}", accountId.value());
			return new AccountNotFoundException("Account not found: " + accountId.value());
		});
		account.deposit(amount);
		Account saved = accountRepository.save(account);
		account.pullDomainEvents().forEach(eventPublisher::publishEvent);
		log.info("[DepositUseCase] Deposit succeeded accountId={} newBalance={}", accountId.value(), saved.balance());
		return saved;
	}
}
