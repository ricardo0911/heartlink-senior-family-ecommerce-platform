package com.heartlink.websocket;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "userId";
    private static final String TOKEN_PARAM = "token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketPushService webSocketPushService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveToken(session);
        Long userId = parseUserId(token);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }

        session.getAttributes().put(ATTR_USER_ID, userId);
        sessionRegistry.register(userId, session);
        webSocketPushService.pushConnected(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("PING".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("PONG"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long userId = getUserId(session);
        if (userId != null) {
            sessionRegistry.remove(userId, session);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserId(session);
        if (userId != null) {
            sessionRegistry.remove(userId, session);
        }
    }

    private String resolveToken(WebSocketSession session) {
        String headerToken = session.getHandshakeHeaders().getFirst(AUTH_HEADER);
        String normalizedHeaderToken = normalizeToken(headerToken);
        if (StringUtils.hasText(normalizedHeaderToken)) {
            return normalizedHeaderToken;
        }

        String protocolToken = session.getHandshakeHeaders().getFirst(WS_PROTOCOL_HEADER);
        if (StringUtils.hasText(protocolToken)) {
            String[] parts = protocolToken.split(",");
            if (parts.length > 0) {
                String normalizedProtocolToken = normalizeToken(parts[0]);
                if (StringUtils.hasText(normalizedProtocolToken)) {
                    return normalizedProtocolToken;
                }
            }
        }

        return getTokenFromUri(session);
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.toLowerCase().startsWith("bearer ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private String getTokenFromUri(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst(TOKEN_PARAM);
    }

    private Long parseUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                return null;
            }
            return Long.parseLong(String.valueOf(loginId));
        } catch (Exception e) {
            log.warn("WebSocket token verify failed", e);
            return null;
        }
    }

    private Long getUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_USER_ID);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
