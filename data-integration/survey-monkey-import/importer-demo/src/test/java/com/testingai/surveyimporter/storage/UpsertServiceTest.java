package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UpsertService.class)
class UpsertServiceTest {

	@Autowired
	private UpsertService upsertService;

	@Autowired
	private SurveyResponseRepository repository;

	@Test
	void insertsWhenAbsent() {
		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", Instant.now(), "{\"a\":1}"));

		SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
		assertThat(saved.getPayload()).isEqualTo("{\"a\":1}");
	}

	@Test
	void updatesWhenIncomingIsNewer() {
		Instant older = Instant.now().minusSeconds(60);
		Instant newer = Instant.now();
		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", older, "old-payload"));

		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", newer, "new-payload"));

		SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
		assertThat(saved.getPayload()).isEqualTo("new-payload");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void noOpsWhenIncomingIsOlderOrEqual() {
		Instant newer = Instant.now();
		Instant older = newer.minusSeconds(60);
		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", newer, "current-payload"));

		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", older, "stale-payload"));

		SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
		assertThat(saved.getPayload()).isEqualTo("current-payload");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void replayingTheSameUpsertAfterAnotherWriterAlreadyWonIsANoOp() {
		Instant now = Instant.now();
		SurveyResponseEntity winner = new SurveyResponseEntity();
		winner.setSurveyId("survey-1");
		winner.setResponseId("resp-1");
		winner.setDateModified(now);
		winner.setPayload("winner");
		winner.setImportedAt(now);
		repository.saveAndFlush(winner);

		upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", now, "loser"));

		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
				.isEqualTo("winner");
	}
}
