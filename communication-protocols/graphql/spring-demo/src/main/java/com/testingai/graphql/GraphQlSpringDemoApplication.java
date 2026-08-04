package com.testingai.graphql;

import com.testingai.graphql.config.QueryLimitsProperties;
import com.testingai.graphql.config.SeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SeedProperties.class, QueryLimitsProperties.class})
public class GraphQlSpringDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GraphQlSpringDemoApplication.class, args);
	}
}
