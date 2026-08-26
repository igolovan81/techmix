package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SurveyResponseRepositoryTest {

	@Autowired
	private SurveyResponseRepository repository;

	@Test
	void updateIfNewerReturnsZeroWhenRowIsAbsent() {
		int updated = repository.updateIfNewer("survey-1", "resp-1", Instant.now(), "{}", Instant.now());

		assertThat(updated).isZero();
	}

	@Test
	void updateIfNewerUpdatesWhenIncomingIsNewer() {
		Instant older = Instant.now().minusSeconds(60);
		SurveyResponseEntity entity = new SurveyResponseEntity();
		entity.setSurveyId("survey-1");
		entity.setResponseId("resp-1");
		entity.setDateModified(older);
		entity.setPayload("old");
		entity.setImportedAt(older);
		repository.saveAndFlush(entity);

		Instant newer = Instant.now();
		int updated = repository.updateIfNewer("survey-1", "resp-1", newer, "new", newer);

		assertThat(updated).isEqualTo(1);
		assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
				.isEqualTo("new");
	}

	@Test
	void updateIfNewerNoOpsWhenIncomingIsOlderOrEqual() {
		Instant current = Instant.now();
		SurveyResponseEntity entity = new SurveyResponseEntity();
		entity.setSurveyId("survey-1");
		entity.setResponseId("resp-1");
		entity.setDateModified(current);
		entity.setPayload("current");
		entity.setImportedAt(current);
		repository.saveAndFlush(entity);

		int updated = repository.updateIfNewer("survey-1", "resp-1", current.minusSeconds(1), "stale", current);

		assertThat(updated).isZero();
		assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
				.isEqualTo("current");
	}
}
