package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "survey_response", uniqueConstraints = @UniqueConstraint(columnNames = {"survey_id", "response_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SurveyResponseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "survey_id", nullable = false)
	private String surveyId;

	@Column(name = "response_id", nullable = false)
	private String responseId;

	@Column(name = "date_modified", nullable = false)
	private Instant dateModified;

	@Column(columnDefinition = "CLOB")
	private String payload;

	@Column(name = "imported_at", nullable = false)
	private Instant importedAt;
}
