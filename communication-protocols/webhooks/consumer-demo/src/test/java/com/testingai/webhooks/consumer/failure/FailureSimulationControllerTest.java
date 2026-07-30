package com.testingai.webhooks.consumer.failure;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FailureSimulationControllerTest {

	private final FailureSimulationState failureSimulationState = new FailureSimulationState();
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new FailureSimulationController(failureSimulationState)).build();

	@Test
	void simulateFailures_armsStateWithGivenCount() throws Exception {
		mockMvc.perform(post("/admin/simulate-failures").param("count", "3")).andExpect(status().isAccepted());

		assertThat(failureSimulationState.remaining()).isEqualTo(3);
	}
}
