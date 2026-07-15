package com.testingai.logging.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final int MAX_LOGGED_BODY_LENGTH = 1000;

	private final RequestLoggingProperties properties;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public RequestLoggingFilter(RequestLoggingProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (isExcluded(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}
		if (properties.includeBody()) {
			doFilterWithBodyLogging(request, response, filterChain);
		} else {
			doFilterWithoutBodyLogging(request, response, filterChain);
		}
	}

	private boolean isExcluded(String path) {
		return properties.excludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
	}

	private void doFilterWithoutBodyLogging(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long start = System.currentTimeMillis();
		filterChain.doFilter(request, response);
		long durationMs = System.currentTimeMillis() - start;
		log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
	}

	private void doFilterWithBodyLogging(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
		long start = System.currentTimeMillis();
		try {
			filterChain.doFilter(wrappedRequest, wrappedResponse);
		} finally {
			long durationMs = System.currentTimeMillis() - start;
			String requestBody = truncatedBody(wrappedRequest.getContentAsByteArray());
			String responseBody = truncatedBody(wrappedResponse.getContentAsByteArray());
			log.info("{} {} -> {} ({} ms) requestBody={} responseBody={}", request.getMethod(),
					request.getRequestURI(), wrappedResponse.getStatus(), durationMs, requestBody, responseBody);
			wrappedResponse.copyBodyToResponse();
		}
	}

	private String truncatedBody(byte[] content) {
		if (content.length == 0) {
			return "";
		}
		int length = Math.min(content.length, MAX_LOGGED_BODY_LENGTH);
		return new String(content, 0, length, StandardCharsets.UTF_8);
	}
}
