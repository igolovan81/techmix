package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sync_watermark")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncWatermarkEntity {

	@Id
	@Column(name = "survey_id")
	private String surveyId;

	@Column(name = "last_synced_at", nullable = false)
	private Instant lastSyncedAt;
}
