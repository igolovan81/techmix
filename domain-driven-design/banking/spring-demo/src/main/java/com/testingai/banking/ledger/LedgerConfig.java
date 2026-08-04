package com.testingai.banking.ledger;

import com.testingai.banking.ledger.domain.TransferService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LedgerConfig {

	@Bean
	TransferService transferService() {
		return new TransferService();
	}
}
