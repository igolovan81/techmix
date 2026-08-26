package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterJobRepository extends JpaRepository<DeadLetterJobEntity, Long> {
}
