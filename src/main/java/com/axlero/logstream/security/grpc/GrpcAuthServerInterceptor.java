package com.axlero.logstream.security.grpc;

import com.axlero.logstream.security.JwtTokenProvider;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@GrpcGlobalServerInterceptor
public class GrpcAuthServerInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(GrpcAuthServerInterceptor.class);

    public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> API_KEY_METADATA_KEY =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> CLIENT_IDENTITY_CONTEXT_KEY = Context.key("client-identity");

    private final JwtTokenProvider tokenProvider;
    private final Set<String> validIngestApiKeys = ConcurrentHashMap.newKeySet();

    public GrpcAuthServerInterceptor(
            JwtTokenProvider tokenProvider,
            @Value("${logstream.grpc.api-keys:super-secret-ingestor-key-2026}") String allowedKeys) {
        this.tokenProvider = tokenProvider;
        for (String k : allowedKeys.split(",")) {
            if (!k.isBlank()) {
                validIngestApiKeys.add(k.trim());
            }
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        // Allow gRPC health checks without auth
        if (methodName.startsWith("grpc.health.v1") || methodName.startsWith("grpc.reflection")) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTHORIZATION_METADATA_KEY);
        String apiKeyHeader = headers.get(API_KEY_METADATA_KEY);

        String authenticatedSubject = null;

        // Ingestion API key
        if (apiKeyHeader != null && validIngestApiKeys.contains(apiKeyHeader)) {
            authenticatedSubject = "daemon-api-key";
        }

        // Bearer JWT validation
        if (authenticatedSubject == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token)) {
                Authentication auth = tokenProvider.getAuthentication(token);
                boolean canIngest = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_INGESTOR") || a.getAuthority().equals("ROLE_ADMIN"));
                if (canIngest) {
                    authenticatedSubject = auth.getName();
                }
            }
        }

        if (authenticatedSubject == null) {
            logger.warn("gRPC call to {} rejected: Missing or invalid credentials", methodName);
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing authentication credentials"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        Context context = Context.current().withValue(CLIENT_IDENTITY_CONTEXT_KEY, authenticatedSubject);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
