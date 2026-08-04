package com.testingai.banking.statements.web;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/{accountId}/statement")
public class StatementController {

	private final StatementRepository statementRepository;

	public StatementController(StatementRepository statementRepository) {
		this.statementRepository = statementRepository;
	}

	@GetMapping
	public List<StatementLine> getStatement(@PathVariable String accountId) {
		return statementRepository.findByAccountId(accountId);
	}
}
