package com.testingai.camunda;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class CamundaDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(CamundaDemoApplication.class, args);
	}
}
