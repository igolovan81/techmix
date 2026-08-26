package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.webhook.WebhookDispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

	private static final Logger log = LoggerFactory.getLogger(AdminController.class);

	private final FailureInjector failureInjector;
	private final WebhookDispatcher webhookDispatcher;

	public AdminController(FailureInjector failureInjector, WebhookDispatcher webhookDispatcher) {
		this.failureInjector = failureInjector;
		this.webhookDispatcher = webhookDispatcher;
	}

	@PostMapping("/failure-mode")
	public FailureConfig setFailureMode(@RequestBody FailureConfig config) {
		log.info("Failure mode changed to {} at rate {}", config.mode(), config.rate());
		failureInjector.configure(config);
		return config;
	}

	@GetMapping("/failure-mode")
	public FailureConfig getFailureMode() {
		return failureInjector.current();
	}

	@PostMapping("/webhooks/trigger")
	public void triggerWebhook(@RequestParam String surveyId, @RequestParam String responseId) {
		log.info("Admin-triggered webhook for survey {} response {}", surveyId, responseId);
		webhookDispatcher.dispatch(surveyId, responseId);
	}
}
