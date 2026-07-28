package com.testingai.grpc.client.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

@GrpcGlobalClientInterceptor
public class RequestIdClientInterceptor implements ClientInterceptor {

	public static final Context.Key<String> REQUEST_ID_CONTEXT_KEY = Context.key("requestId");
	public static final Metadata.Key<String> REQUEST_ID_METADATA_KEY = Metadata.Key.of("x-request-id",
			Metadata.ASCII_STRING_MARSHALLER);

	@Override
	public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
			CallOptions callOptions, Channel next) {
		return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {

			@Override
			public void start(Listener<RespT> responseListener, Metadata headers) {
				String requestId = REQUEST_ID_CONTEXT_KEY.get();
				if (requestId != null) {
					headers.put(REQUEST_ID_METADATA_KEY, requestId);
				}
				super.start(responseListener, headers);
			}
		};
	}
}
