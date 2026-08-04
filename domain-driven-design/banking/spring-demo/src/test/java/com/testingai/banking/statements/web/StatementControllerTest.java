package com.testingai.banking.statements.web;

import static org.hamcrest.Matchers.hasSize;
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
class StatementControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void openingAndDepositingProducesTwoCreditStatementLinesInOrder() throws Exception {
		String openResponse = mockMvc
				.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
						.content("{\"ownerName\":\"Ada Lovelace\",\"initialBalance\":100.00,\"currency\":\"USD\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String accountId = com.jayway.jsonpath.JsonPath.read(openResponse, "$.accountId");

		mockMvc.perform(post("/accounts/" + accountId + "/deposits").contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":50.00,\"currency\":\"USD\"}")).andExpect(status().isOk());

		mockMvc.perform(get("/accounts/" + accountId + "/statement")).andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].description").value("Account opened"))
				.andExpect(jsonPath("$[0].type").value("CREDIT"))
				.andExpect(jsonPath("$[1].description").value("Deposit"))
				.andExpect(jsonPath("$[1].type").value("CREDIT"));
	}
}
