package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.TransferService;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferMoneyUseCase {

	private final AccountRepository accountRepository;
	private final TransferService transferService;
	private final ApplicationEventPublisher eventPublisher;

	public TransferMoneyUseCase(AccountRepository accountRepository, TransferService transferService,
			ApplicationEventPublisher eventPublisher) {
		this.accountRepository = accountRepository;
		this.transferService = transferService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public UUID transfer(AccountId fromAccountId, AccountId toAccountId, Money amount) {
		Account source = accountRepository.findById(fromAccountId)
				.orElseThrow(() -> new AccountNotFoundException("Account not found: " + fromAccountId.value()));
		Account target = accountRepository.findById(toAccountId)
				.orElseThrow(() -> new AccountNotFoundException("Account not found: " + toAccountId.value()));

		transferService.transfer(source, target, amount);

		accountRepository.save(source);
		accountRepository.save(target);

		source.pullDomainEvents().forEach(eventPublisher::publishEvent);
		target.pullDomainEvents().forEach(eventPublisher::publishEvent);

		return UUID.randomUUID();
	}
}
