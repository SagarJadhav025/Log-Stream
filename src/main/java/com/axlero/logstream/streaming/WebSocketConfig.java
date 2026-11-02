package com.axlero.logstream.streaming;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the WebSocket endpoints for real-time Live Tail streaming.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveTailWebSocketHandler liveTailWebSocketHandler;

    public WebSocketConfig(LiveTailWebSocketHandler liveTailWebSocketHandler) {
        this.liveTailWebSocketHandler = liveTailWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveTailWebSocketHandler, "/ws/logs")
                .setAllowedOrigins("*");
    }
}
