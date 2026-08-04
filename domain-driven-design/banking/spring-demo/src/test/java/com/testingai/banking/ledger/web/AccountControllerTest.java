package com.testingai.banking.ledger.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void opensDepositsWithdrawsAndTransfersBetweenAccounts() throws Exception {
		String aliceId = openAccount("Alice", "200.00", "USD");
		String bobId = openAccount("Bob", "50.00", "USD");

		mockMvc.perform(post("/accounts/" + aliceId + "/deposits").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100.00,\"currency\":\"USD\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(300.00));

		mockMvc.perform(post("/accounts/" + aliceId + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":50.00,\"currency\":\"USD\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(250.00));

		mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"" + aliceId + "\",\"toAccountId\":\"" + bobId
						+ "\",\"amount\":75.00,\"currency\":\"USD\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.transferId").exists());

		mockMvc.perform(get("/accounts/" + aliceId)).andExpect(jsonPath("$.balance").value(175.00));
		mockMvc.perform(get("/accounts/" + bobId)).andExpect(jsonPath("$.balance").value(125.00));
	}

	private String openAccount(String ownerName, String initialBalance, String currency) throws Exception {
		String response = mockMvc
				.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
						.content("{\"ownerName\":\"" + ownerName + "\",\"initialBalance\":" + initialBalance
								+ ",\"currency\":\"" + currency + "\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.accountId");
	}
}
