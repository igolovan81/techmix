package com.testingai.banking.statements.infrastructure;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaStatementRepositoryAdapter implements StatementRepository {

	private final SpringDataStatementRepository springDataStatementRepository;

	public JpaStatementRepositoryAdapter(SpringDataStatementRepository springDataStatementRepository) {
		this.springDataStatementRepository = springDataStatementRepository;
	}

	@Override
	public void save(StatementLine line) {
		springDataStatementRepository.save(StatementLineMapper.toEntity(line));
	}

	@Override
	public List<StatementLine> findByAccountId(String accountId) {
		return springDataStatementRepository.findByAccountIdOrderByOccurredAtAsc(accountId).stream()
				.map(StatementLineMapper::toDomain).toList();
	}
}
