package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record TransferRequest(String fromAccountId, String toAccountId, BigDecimal amount, String currency) {
}
