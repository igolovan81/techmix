package com.testingai.banking.ledger.web.dto;

import java.math.BigDecimal;

public record OpenAccountRequest(String ownerName, BigDecimal initialBalance, String currency) {
}
