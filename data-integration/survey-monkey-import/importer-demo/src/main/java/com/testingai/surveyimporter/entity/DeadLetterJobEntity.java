package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_job")
@Getter
@Setter
@NoArgsConstructor
public class DeadLetterJobEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "survey_id", nullable = false)
	private String surveyId;

	private String kind;

	private String cursor;

	@Column(name = "response_id")
	private String responseId;

	@Column(name = "trigger_type")
	private String triggerType;

	@Column(name = "attempt_count")
	private int attemptCount;

	@Column(name = "error_class")
	private String errorClass;

	@Column(name = "error_message", columnDefinition = "CLOB")
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;
}
