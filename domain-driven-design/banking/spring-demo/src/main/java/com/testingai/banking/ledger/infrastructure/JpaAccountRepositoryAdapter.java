package com.testingai.banking.ledger.infrastructure;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaAccountRepositoryAdapter implements AccountRepository {

	private final SpringDataAccountRepository springDataAccountRepository;

	public JpaAccountRepositoryAdapter(SpringDataAccountRepository springDataAccountRepository) {
		this.springDataAccountRepository = springDataAccountRepository;
	}

	@Override
	public Account save(Account account) {
		AccountJpaEntity saved = springDataAccountRepository.save(AccountMapper.toEntity(account));
		return AccountMapper.toDomain(saved);
	}

	@Override
	public Optional<Account> findById(AccountId id) {
		return springDataAccountRepository.findById(id.value()).map(AccountMapper::toDomain);
	}
}
