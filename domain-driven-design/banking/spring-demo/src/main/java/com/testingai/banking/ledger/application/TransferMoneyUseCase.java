package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.TransferService;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
		log.info("[TransferMoneyUseCase] Transferring from={} to={} amount={}", fromAccountId.value(),
				toAccountId.value(), amount);
		Account source = accountRepository.findById(fromAccountId).orElseThrow(() -> {
			log.warn("[TransferMoneyUseCase] Source account not found id={}", fromAccountId.value());
			return new AccountNotFoundException("Account not found: " + fromAccountId.value());
		});
		Account target = accountRepository.findById(toAccountId).orElseThrow(() -> {
			log.warn("[TransferMoneyUseCase] Target account not found id={}", toAccountId.value());
			return new AccountNotFoundException("Account not found: " + toAccountId.value());
		});

		transferService.transfer(source, target, amount);

		accountRepository.save(source);
		accountRepository.save(target);

		source.pullDomainEvents().forEach(eventPublisher::publishEvent);
		target.pullDomainEvents().forEach(eventPublisher::publishEvent);

		UUID transferId = UUID.randomUUID();
		log.info("[TransferMoneyUseCase] Transfer succeeded transferId={} from={} to={}", transferId,
				fromAccountId.value(), toAccountId.value());
		return transferId;
	}
}
