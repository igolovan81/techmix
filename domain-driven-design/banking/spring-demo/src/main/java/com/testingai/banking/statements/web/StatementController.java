package com.testingai.banking.statements.web;

import com.testingai.banking.statements.domain.StatementLine;
import com.testingai.banking.statements.domain.StatementRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/accounts/{accountId}/statement")
public class StatementController {

	private final StatementRepository statementRepository;

	public StatementController(StatementRepository statementRepository) {
		this.statementRepository = statementRepository;
	}

	@GetMapping
	public List<StatementLine> getStatement(@PathVariable String accountId) {
		log.info("[StatementController] GET /accounts/{}/statement", accountId);
		List<StatementLine> lines = statementRepository.findByAccountId(accountId);
		log.info("[StatementController] Statement for accountId={} returned {} lines", accountId, lines.size());
		return lines;
	}
}
