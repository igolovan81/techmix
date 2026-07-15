package com.testingai.logging.autoconfigure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

	@Test
	void excludedPath_shouldSkipLoggingAndPassRequestThroughUnwrapped() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, true, List.of("/actuator/**"));
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		try {
			filter.doFilter(request, response, chain);

			assertThat(chain.getRequest()).isSameAs(request);
			assertThat(appender.list).isEmpty();
		} finally {
			detachAppender(appender);
		}
	}

	@Test
	void includedPath_withoutBodyLogging_shouldLogMethodPathAndStatus() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, false, List.of());
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		ListAppender<ILoggingEvent> appender = attachAppender();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/hello");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(200);
		MockFilterChain chain = new MockFilterChain();

		try {
			filter.doFilter(request, response, chain);

			assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("GET")
					&& event.getFormattedMessage().contains("/demo/hello")
					&& event.getFormattedMessage().contains("200"));
		} finally {
			detachAppender(appender);
		}
	}

	@Test
	void includedPath_withBodyLogging_shouldStillDeliverResponseBodyToClient() throws Exception {
		RequestLoggingProperties properties = new RequestLoggingProperties(true, true, List.of());
		RequestLoggingFilter filter = new RequestLoggingFilter(properties);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/demo/echo");
		request.setContent("{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
				resp.setStatus(200);
				resp.getWriter().write("{\"message\":\"hi\"}");
			}
		});

		filter.doFilter(request, response, chain);

		assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"hi\"}");
		assertThat(response.getStatus()).isEqualTo(200);
	}

	private ListAppender<ILoggingEvent> attachAppender() {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}

	private void detachAppender(ListAppender<ILoggingEvent> appender) {
		((Logger) LoggerFactory.getLogger(RequestLoggingFilter.class)).detachAppender(appender);
	}
}
