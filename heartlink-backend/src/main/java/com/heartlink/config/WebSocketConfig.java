package com.heartlink.config;

import com.heartlink.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080,http://localhost:8081,http://127.0.0.1:8081}")
    private String[] allowedOriginPatterns;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] patterns = allowedOriginPatterns;
        if (patterns == null || patterns.length == 0) {
            patterns = new String[]{"*"};
        } else if (Arrays.stream(patterns).noneMatch("*"::equals)) {
            patterns = Arrays.copyOf(patterns, patterns.length + 1);
            patterns[patterns.length - 1] = "*";
        }
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOriginPatterns(patterns);
    }
}
