package com.axlero.logstream.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the WebSocket endpoints for real-time Live Tail streaming with token authentication.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveTailWebSocketHandler liveTailWebSocketHandler;
    private final JwtWebSocketHandshakeInterceptor handshakeInterceptor;

    @Value("${logstream.security.cors.allowed-origin:http://localhost:3000}")
    private String allowedOrigin;

    public WebSocketConfig(LiveTailWebSocketHandler liveTailWebSocketHandler,
                           JwtWebSocketHandshakeInterceptor handshakeInterceptor) {
        this.liveTailWebSocketHandler = liveTailWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveTailWebSocketHandler, "/ws/livetail", "/ws/logs")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(allowedOrigin, "http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:5173");
    }
}
