package com.testingai.banking.ledger.domain;

import java.util.Optional;

public interface AccountRepository {

	Account save(Account account);

	Optional<Account> findById(AccountId id);
}
