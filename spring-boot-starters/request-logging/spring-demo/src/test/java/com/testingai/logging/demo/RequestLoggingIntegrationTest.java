package com.testingai.logging.demo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.testingai.logging.autoconfigure.RequestLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void attachLogAppender() {
		logAppender = new ListAppender<>();
		logAppender.start();
		filterLogger().addAppender(logAppender);
	}

	@AfterEach
	void detachLogAppender() {
		filterLogger().detachAppender(logAppender);
	}

	private Logger filterLogger() {
		return (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
	}

	@Test
	void helloEndpoint_logsRequestLine() throws Exception {
		mockMvc.perform(get("/demo/hello")).andExpect(status().isOk());

		assertThat(formattedMessages()).anyMatch(
				message -> message.contains("GET") && message.contains("/demo/hello") && message.contains("200"));
	}

	@Test
	void echoEndpoint_logsRequestAndResponseBody() throws Exception {
		mockMvc.perform(post("/demo/echo").contentType("application/json").content("{\"message\":\"hi\"}"))
				.andExpect(status().isOk());

		assertThat(formattedMessages()).anyMatch(message -> message.contains("hi"));
	}

	@Test
	void actuatorHealth_isExcludedFromLogging() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		assertThat(formattedMessages()).isEmpty();
	}

	private List<String> formattedMessages() {
		return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}
}
