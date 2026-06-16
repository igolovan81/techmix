package com.testingai.rabbitmq;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(title = "RabbitMQ Demo API", version = "1.0.0", description = "Learning project demonstrating RabbitMQ messaging patterns"))
@SpringBootApplication
public class RabbitMqDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RabbitMqDemoApplication.class, args);
	}
}
