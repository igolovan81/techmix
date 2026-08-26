package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SyncWatermarkEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncWatermarkRepository extends JpaRepository<SyncWatermarkEntity, String> {
}
