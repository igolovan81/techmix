package com.testingai.grpc.server.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdServerInterceptorTest {

	@Mock
	private ServerCall<String, String> call;
	@Mock
	private ServerCallHandler<String, String> next;
	@Mock
	private ServerCall.Listener<String> listener;

	private final RequestIdServerInterceptor interceptor = new RequestIdServerInterceptor();

	@Test
	void interceptCall_propagatesRequestIdFromHeader_whenPresent() {
		Metadata headers = new Metadata();
		headers.put(RequestIdServerInterceptor.REQUEST_ID_METADATA_KEY, "abc12345");
		when(next.startCall(eq(call), eq(headers))).thenAnswer(invocation -> {
			assertThat(RequestIdServerInterceptor.REQUEST_ID_CONTEXT_KEY.get()).isEqualTo("abc12345");
			return listener;
		});

		interceptor.interceptCall(call, headers, next);

		verify(next).startCall(call, headers);
	}

	@Test
	void interceptCall_generatesFallbackRequestId_whenHeaderMissing() {
		Metadata headers = new Metadata();
		when(next.startCall(eq(call), eq(headers))).thenAnswer(invocation -> {
			assertThat(RequestIdServerInterceptor.REQUEST_ID_CONTEXT_KEY.get()).isNotBlank();
			return listener;
		});

		interceptor.interceptCall(call, headers, next);

		verify(next).startCall(call, headers);
	}
}
