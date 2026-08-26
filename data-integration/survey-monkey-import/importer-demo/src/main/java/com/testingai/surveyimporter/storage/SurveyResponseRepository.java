package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponseEntity, Long> {

	Optional<SurveyResponseEntity> findBySurveyIdAndResponseId(String surveyId, String responseId);

	List<SurveyResponseEntity> findBySurveyId(String surveyId);

	@Modifying(clearAutomatically = true)
	@Query(value = "UPDATE survey_response SET date_modified = :dateModified, payload = :payload, "
			+ "imported_at = :importedAt WHERE survey_id = :surveyId AND response_id = :responseId "
			+ "AND date_modified < :dateModified", nativeQuery = true)
	int updateIfNewer(@Param("surveyId") String surveyId, @Param("responseId") String responseId,
			@Param("dateModified") Instant dateModified, @Param("payload") String payload,
			@Param("importedAt") Instant importedAt);
}
