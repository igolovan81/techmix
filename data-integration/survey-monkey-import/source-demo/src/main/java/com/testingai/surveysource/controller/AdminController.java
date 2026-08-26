package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.webhook.WebhookDispatcher;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

	private final FailureInjector failureInjector;
	private final WebhookDispatcher webhookDispatcher;

	public AdminController(FailureInjector failureInjector, WebhookDispatcher webhookDispatcher) {
		this.failureInjector = failureInjector;
		this.webhookDispatcher = webhookDispatcher;
	}

	@PostMapping("/failure-mode")
	public FailureConfig setFailureMode(@RequestBody FailureConfig config) {
		failureInjector.configure(config);
		return config;
	}

	@GetMapping("/failure-mode")
	public FailureConfig getFailureMode() {
		return failureInjector.current();
	}

	@PostMapping("/webhooks/trigger")
	public void triggerWebhook(@RequestParam String surveyId, @RequestParam String responseId) {
		webhookDispatcher.dispatch(surveyId, responseId);
	}
}
