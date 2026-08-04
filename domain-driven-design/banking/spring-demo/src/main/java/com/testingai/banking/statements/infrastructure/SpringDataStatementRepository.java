package com.testingai.banking.statements.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataStatementRepository extends JpaRepository<StatementLineJpaEntity, UUID> {

	List<StatementLineJpaEntity> findByAccountIdOrderByOccurredAtAsc(String accountId);
}
