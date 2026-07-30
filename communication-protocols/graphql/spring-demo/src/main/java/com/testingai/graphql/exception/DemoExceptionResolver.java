package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Classifies exceptions thrown from data fetchers into typed GraphQL errors instead of leaking a raw stack trace:
 * {@link IllegalArgumentException} (e.g. {@code addReview} against an unknown product) becomes {@code BAD_REQUEST};
 * {@link AccessDeniedException} (thrown by {@code @PreAuthorize} — always as its subtype
 * {@code AuthorizationDeniedException}, for both "not authenticated" and "wrong role", verified against the
 * spring-security-core jar) becomes {@code UNAUTHORIZED} or {@code FORBIDDEN} depending on whether the current
 * {@link SecurityContextHolder} authentication represents a real, logged-in principal; anything else — including
 * {@link com.testingai.graphql.util.FailureSimulator}'s simulated failures — becomes {@code INTERNAL_ERROR}.
 */
@Component
public class DemoExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		return GraphqlErrorBuilder.newError().errorType(classify(ex)).message(ex.getMessage()).build();
	}

	private ErrorType classify(Throwable ex) {
		if (ex instanceof IllegalArgumentException) {
			return ErrorType.BAD_REQUEST;
		}
		if (ex instanceof AccessDeniedException) {
			return isAnonymous() ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN;
		}
		return ErrorType.INTERNAL_ERROR;
	}

	private boolean isAnonymous() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null || authentication instanceof AnonymousAuthenticationToken;
	}
}
