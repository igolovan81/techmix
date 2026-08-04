package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawUseCase {

	private final AccountRepository accountRepository;
	private final ApplicationEventPublisher eventPublisher;

	public WithdrawUseCase(AccountRepository accountRepository, ApplicationEventPublisher eventPublisher) {
		this.accountRepository = accountRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public Account withdraw(AccountId accountId, Money amount) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId.value()));
		account.withdraw(amount);
		Account saved = accountRepository.save(account);
		account.pullDomainEvents().forEach(eventPublisher::publishEvent);
		return saved;
	}
}
