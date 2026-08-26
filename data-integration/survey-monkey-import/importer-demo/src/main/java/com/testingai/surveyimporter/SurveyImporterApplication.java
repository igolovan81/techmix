package com.testingai.surveyimporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurveyImporterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SurveyImporterApplication.class, args);
	}
}
