package com.testingai.banking.ledger.web;

import com.testingai.banking.ledger.application.TransferMoneyUseCase;
import com.testingai.banking.ledger.domain.AccountId;
import com.testingai.banking.ledger.domain.Money;
import com.testingai.banking.ledger.web.dto.TransferRequest;
import com.testingai.banking.ledger.web.dto.TransferResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

	private final TransferMoneyUseCase transferMoneyUseCase;

	public TransferController(TransferMoneyUseCase transferMoneyUseCase) {
		this.transferMoneyUseCase = transferMoneyUseCase;
	}

	@PostMapping("/transfers")
	public TransferResponse transfer(@RequestBody TransferRequest request) {
		UUID transferId = transferMoneyUseCase.transfer(new AccountId(UUID.fromString(request.fromAccountId())),
				new AccountId(UUID.fromString(request.toAccountId())), Money.of(request.amount(), request.currency()));
		return new TransferResponse(transferId.toString());
	}
}
