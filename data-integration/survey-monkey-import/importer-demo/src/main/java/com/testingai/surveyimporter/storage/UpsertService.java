package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UpsertService {

	private final SurveyResponseRepository repository;

	public UpsertService(SurveyResponseRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void upsert(SurveyResponseUpsert upsert) {
		Instant importedAt = Instant.now();
		int updated = repository.updateIfNewer(upsert.surveyId(), upsert.responseId(), upsert.dateModified(),
				upsert.payload(), importedAt);
		if (updated > 0) {
			return;
		}
		boolean exists = repository.findBySurveyIdAndResponseId(upsert.surveyId(), upsert.responseId()).isPresent();
		if (exists) {
			return;
		}
		SurveyResponseEntity entity = new SurveyResponseEntity();
		entity.setSurveyId(upsert.surveyId());
		entity.setResponseId(upsert.responseId());
		entity.setDateModified(upsert.dateModified());
		entity.setPayload(upsert.payload());
		entity.setImportedAt(importedAt);
		repository.save(entity);
	}
}
