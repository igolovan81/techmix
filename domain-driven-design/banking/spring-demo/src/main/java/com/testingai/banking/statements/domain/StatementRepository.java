package com.testingai.banking.statements.domain;

import java.util.List;

public interface StatementRepository {

	void save(StatementLine line);

	List<StatementLine> findByAccountId(String accountId);
}
