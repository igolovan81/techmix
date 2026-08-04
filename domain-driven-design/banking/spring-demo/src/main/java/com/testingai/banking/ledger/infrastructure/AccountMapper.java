package com.testingai.banking.ledger.infrastructure;

import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import java.util.Currency;

public final class AccountMapper {

	private AccountMapper() {
	}

	public static AccountJpaEntity toEntity(Account account) {
		return new AccountJpaEntity(account.id().value(), account.ownerName(), account.balance().amount(),
				account.balance().currency().getCurrencyCode());
	}

	public static Account toDomain(AccountJpaEntity entity) {
		return Account.reconstitute(new AccountId(entity.getId()), entity.getOwnerName(),
				new Money(entity.getBalanceAmount(), Currency.getInstance(entity.getBalanceCurrency())));
	}
}
