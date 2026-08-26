package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.domain.Links;
import com.testingai.surveysource.domain.ResponsesPage;
import com.testingai.surveysource.domain.SourceSurveyResponse;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.seed.SeedDataService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v3/surveys/{surveyId}/responses")
public class ResponsesController {

	private static final Logger log = LoggerFactory.getLogger(ResponsesController.class);
	private static final int MAX_PAGE_SIZE = 100;

	private final SeedDataService seedDataService;
	private final FailureInjector failureInjector;

	public ResponsesController(SeedDataService seedDataService, FailureInjector failureInjector) {
		this.seedDataService = seedDataService;
		this.failureInjector = failureInjector;
	}

	@GetMapping("/bulk")
	public ResponseEntity<Object> bulk(@PathVariable String surveyId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(name = "per_page", defaultValue = "25") int perPage,
			@RequestParam(name = "start_modified_at", required = false) String startModifiedAt) {

		if (failureInjector.shouldInject(FailureMode.RATE_LIMIT)) {
			log.warn("Injecting RATE_LIMIT failure for survey {} page {}", surveyId, page);
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2").build();
		}
		if (failureInjector.shouldInject(FailureMode.SERVER_ERROR)) {
			log.warn("Injecting SERVER_ERROR failure for survey {} page {}", surveyId, page);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		int size = Math.min(perPage, MAX_PAGE_SIZE);
		List<SourceSurveyResponse> all = seedDataService.responsesFor(surveyId, parseInstant(startModifiedAt));
		int total = all.size();
		int fromIndex = Math.min((page - 1) * size, total);
		int toIndex = Math.min(fromIndex + size, total);
		List<SourceSurveyResponse> pageData = new ArrayList<>(all.subList(fromIndex, toIndex));

		if (failureInjector.shouldInject(FailureMode.MALFORMED) && !pageData.isEmpty()) {
			SourceSurveyResponse original = pageData.get(0);
			log.warn("Injecting MALFORMED response for survey {} page {} (response id nulled)", surveyId, page);
			pageData.set(0,
					new SourceSurveyResponse(null, original.surveyId(), original.dateModified(), original.answers()));
		}

		boolean hasNext = toIndex < total;
		Links links = new Links(hasNext ? String.valueOf(page + 1) : null);
		log.info("Served survey {} page {} ({} responses, hasNext={})", surveyId, page, pageData.size(), hasNext);
		return ResponseEntity.ok(new ResponsesPage(pageData, page, size, total, links));
	}

	@GetMapping("/{responseId}")
	public ResponseEntity<SourceSurveyResponse> single(@PathVariable String surveyId, @PathVariable String responseId) {
		if (failureInjector.shouldInject(FailureMode.SERVER_ERROR)) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return seedDataService.findResponse(surveyId, responseId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	private Instant parseInstant(String value) {
		return value == null ? null : Instant.parse(value);
	}
}
