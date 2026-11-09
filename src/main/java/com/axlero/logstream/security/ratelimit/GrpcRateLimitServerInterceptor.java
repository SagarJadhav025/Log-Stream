package com.axlero.logstream.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@GrpcGlobalServerInterceptor
public class GrpcRateLimitServerInterceptor implements ServerInterceptor {

    private final Map<String, Bucket> clientBuckets = new ConcurrentHashMap<>();

    private Bucket createIngestBucket() {
        // 10,000 logs/second refill, capacity 20,000 burst
        Bandwidth limit = Bandwidth.classic(20_000, Refill.intervally(10_000, Duration.ofSeconds(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        String clientKey = remoteAddr != null ? remoteAddr.toString() : "unknown-client";
        Bucket bucket = clientBuckets.computeIfAbsent(clientKey, k -> createIngestBucket());

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                if (!bucket.tryConsume(1)) {
                    call.close(Status.RESOURCE_EXHAUSTED.withDescription("Ingestion rate limit exceeded"), new Metadata());
                    return;
                }
                super.onMessage(message);
            }
        };
    }
}
