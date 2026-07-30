package com.testingai.webhooks.consumer.failure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class FailureSimulationController {

	private final FailureSimulationState failureSimulationState;

	public FailureSimulationController(FailureSimulationState failureSimulationState) {
		this.failureSimulationState = failureSimulationState;
	}

	@PostMapping("/simulate-failures")
	public ResponseEntity<Void> simulateFailures(@RequestParam int count) {
		failureSimulationState.arm(count);
		return ResponseEntity.accepted().build();
	}
}
