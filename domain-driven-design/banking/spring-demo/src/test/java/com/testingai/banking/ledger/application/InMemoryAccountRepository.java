package com.testingai.banking.ledger.application;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryAccountRepository implements AccountRepository {

	private final Map<AccountId, Account> accounts = new HashMap<>();

	@Override
	public Account save(Account account) {
		accounts.put(account.id(), account);
		return account;
	}

	@Override
	public Optional<Account> findById(AccountId id) {
		return Optional.ofNullable(accounts.get(id));
	}
}
