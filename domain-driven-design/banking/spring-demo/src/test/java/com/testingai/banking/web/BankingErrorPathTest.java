package com.testingai.banking.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BankingErrorPathTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void withdrawingBeyondBalanceReturns400() throws Exception {
		String accountId = openAccount("10.00");

		mockMvc.perform(post("/accounts/" + accountId + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100.00,\"currency\":\"USD\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("InsufficientFundsException"));
	}

	@Test
	void gettingUnknownAccountReturns404() throws Exception {
		mockMvc.perform(get("/accounts/" + UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
	}

	@Test
	void depositingMismatchedCurrencyReturns400() throws Exception {
		String accountId = openAccount("10.00");

		mockMvc.perform(post("/accounts/" + accountId + "/deposits").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":10.00,\"currency\":\"EUR\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("CurrencyMismatchException"));
	}

	private String openAccount(String initialBalance) throws Exception {
		String response = mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(
				"{\"ownerName\":\"Ada Lovelace\",\"initialBalance\":" + initialBalance + ",\"currency\":\"USD\"}"))
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.accountId");
	}
}
