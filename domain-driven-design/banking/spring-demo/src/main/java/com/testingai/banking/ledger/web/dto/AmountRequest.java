package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record AmountRequest(BigDecimal amount, String currency) {
}
