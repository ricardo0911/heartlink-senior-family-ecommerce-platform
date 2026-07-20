package com.heartlink.websocket;

import com.alibaba.fastjson2.JSON;
import com.heartlink.entity.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketPushService {

    public static final String TYPE_WS_CONNECTED = "WS_CONNECTED";
    public static final String TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String TYPE_SOS_ALERT = "SOS_ALERT";

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketPushService(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void pushConnected(Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        pushToUser(userId, TYPE_WS_CONNECTED, data);
    }

    public void pushChatMessage(Message message) {
        pushToUser(message.getReceiverId(), TYPE_CHAT_MESSAGE, message);
    }

    public void pushSosAlert(Long receiverId, Map<String, Object> sosPayload) {
        pushToUser(receiverId, TYPE_SOS_ALERT, sosPayload);
    }

    public void pushToUser(Long userId, String type, Object data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("timestamp", LocalDateTime.now());
        payload.put("data", data);
        sessionRegistry.sendToUser(userId, JSON.toJSONString(payload));
    }
}
