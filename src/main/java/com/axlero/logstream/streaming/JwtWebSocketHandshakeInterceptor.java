package com.axlero.logstream.streaming;

import com.axlero.logstream.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtWebSocketHandshakeInterceptor.class);
    private final JwtTokenProvider tokenProvider;

    public JwtWebSocketHandshakeInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");

            // Also check Authorization header if passed
            if (token == null || token.isBlank()) {
                String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && tokenProvider.validateToken(token)) {
                Authentication auth = tokenProvider.getAuthentication(token);
                boolean isAuthorized = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER") || a.getAuthority().equals("ROLE_ADMIN"));

                if (isAuthorized) {
                    attributes.put("auth", auth);
                    attributes.put("username", auth.getName());
                    return true;
                }
            }
        }

        logger.warn("WebSocket handshake rejected: Missing or invalid JWT credentials from {}", request.getRemoteAddress());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
