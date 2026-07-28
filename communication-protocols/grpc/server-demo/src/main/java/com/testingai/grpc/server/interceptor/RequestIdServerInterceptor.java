package com.testingai.grpc.server.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import java.util.UUID;

@GrpcGlobalServerInterceptor
public class RequestIdServerInterceptor implements ServerInterceptor {

	public static final Context.Key<String> REQUEST_ID_CONTEXT_KEY = Context.key("requestId");
	public static final Metadata.Key<String> REQUEST_ID_METADATA_KEY = Metadata.Key.of("x-request-id",
			Metadata.ASCII_STRING_MARSHALLER);

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		String requestId = headers.get(REQUEST_ID_METADATA_KEY);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString().substring(0, 8);
		}
		Context context = Context.current().withValue(REQUEST_ID_CONTEXT_KEY, requestId);
		return Contexts.interceptCall(context, call, headers, next);
	}
}
