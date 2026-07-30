package com.testingai.webhooks.producer.config;

import com.testingai.webhooks.producer.delivery.RetryBackoffSchedule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class DispatchConfig {

	@Bean
	public RestClient webhookRestClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(2));
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	@Bean
	public TaskScheduler webhookTaskScheduler() {
		SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
		taskScheduler.setVirtualThreads(true);
		taskScheduler.setThreadNamePrefix("webhook-retry-");
		return taskScheduler;
	}

	@Bean
	public RetryBackoffSchedule retryBackoffSchedule() {
		return new RetryBackoffSchedule(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4),
				Duration.ofSeconds(8), Duration.ofSeconds(16)));
	}
}
