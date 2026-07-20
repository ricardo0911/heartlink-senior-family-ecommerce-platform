package com.heartlink.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        if (session != null) {
            sessions.remove(session);
        }
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
        log.info("WebSocket disconnected: userId={}, sessionId={}", userId, session != null ? session.getId() : "N/A");
    }

    public void sendToUser(Long userId, String payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                remove(userId, session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("WebSocket push failed: userId={}, sessionId={}", userId, session.getId(), e);
                remove(userId, session);
            }
        }
    }
}
