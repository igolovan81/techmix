package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo/dlq")
public class DlqController {

	private final DeadLetterService deadLetterService;

	public DlqController(DeadLetterService deadLetterService) {
		this.deadLetterService = deadLetterService;
	}

	@GetMapping
	public List<DeadLetterJobEntity> list() {
		return deadLetterService.list();
	}

	@PostMapping("/{id}/redrive")
	public void redrive(@PathVariable Long id) {
		deadLetterService.redrive(id);
	}
}
