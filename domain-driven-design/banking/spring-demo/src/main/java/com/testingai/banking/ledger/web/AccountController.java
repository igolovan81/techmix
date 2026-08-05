package com.testingai.banking.ledger.web;

import com.testingai.banking.ledger.application.DepositUseCase;
import com.testingai.banking.ledger.application.OpenAccountUseCase;
import com.testingai.banking.ledger.application.WithdrawUseCase;
import com.testingai.banking.ledger.domain.Account;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.AccountRepository;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.domain.exception.AccountNotFoundException;
import com.testingai.banking.ledger.web.dto.AccountResponse;
import com.testingai.banking.ledger.web.dto.AmountRequest;
import com.testingai.banking.ledger.web.dto.OpenAccountRequest;
import com.testingai.banking.ledger.web.dto.OpenAccountResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/accounts")
public class AccountController {

	private final OpenAccountUseCase openAccountUseCase;
	private final DepositUseCase depositUseCase;
	private final WithdrawUseCase withdrawUseCase;
	private final AccountRepository accountRepository;

	public AccountController(OpenAccountUseCase openAccountUseCase, DepositUseCase depositUseCase,
			WithdrawUseCase withdrawUseCase, AccountRepository accountRepository) {
		this.openAccountUseCase = openAccountUseCase;
		this.depositUseCase = depositUseCase;
		this.withdrawUseCase = withdrawUseCase;
		this.accountRepository = accountRepository;
	}

	@PostMapping
	public ResponseEntity<OpenAccountResponse> open(@RequestBody OpenAccountRequest request) {
		log.info("[AccountController] POST /accounts owner={}", request.ownerName());
		Account account = openAccountUseCase.open(request.ownerName(),
				Money.of(request.initialBalance(), request.currency()));
		return ResponseEntity.status(HttpStatus.CREATED).body(new OpenAccountResponse(account.id().value().toString()));
	}

	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable String id) {
		log.info("[AccountController] GET /accounts/{}", id);
		AccountId accountId = new AccountId(UUID.fromString(id));
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
		return AccountResponse.from(account);
	}

	@PostMapping("/{id}/deposits")
	public AccountResponse deposit(@PathVariable String id, @RequestBody AmountRequest request) {
		log.info("[AccountController] POST /accounts/{}/deposits amount={}", id, request.amount());
		Account account = depositUseCase.deposit(new AccountId(UUID.fromString(id)),
				Money.of(request.amount(), request.currency()));
		return AccountResponse.from(account);
	}

	@PostMapping("/{id}/withdrawals")
	public AccountResponse withdraw(@PathVariable String id, @RequestBody AmountRequest request) {
		log.info("[AccountController] POST /accounts/{}/withdrawals amount={}", id, request.amount());
		Account account = withdrawUseCase.withdraw(new AccountId(UUID.fromString(id)),
				Money.of(request.amount(), request.currency()));
		return AccountResponse.from(account);
	}
}
