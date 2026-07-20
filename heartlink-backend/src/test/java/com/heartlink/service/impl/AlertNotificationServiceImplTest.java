package com.heartlink.service.impl;

import com.heartlink.config.NotificationProperties;
import com.heartlink.config.WeChatMiniProgramProperties;
import com.heartlink.entity.Message;
import com.heartlink.entity.User;
import com.heartlink.service.NotificationWebhookClient;
import com.heartlink.service.WeChatMiniProgramService;
import com.heartlink.websocket.WebSocketPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertNotificationServiceImplTest {

    @Mock
    private WebSocketPushService webSocketPushService;
    @Mock
    private NotificationWebhookClient notificationWebhookClient;
    @Mock
    private WeChatMiniProgramService weChatMiniProgramService;

    private NotificationProperties notificationProperties;
    private WeChatMiniProgramProperties weChatMiniProgramProperties;
    private AlertNotificationServiceImpl alertNotificationService;

    @BeforeEach
    void setUp() {
        notificationProperties = new NotificationProperties();
        weChatMiniProgramProperties = new WeChatMiniProgramProperties();
        alertNotificationService = new AlertNotificationServiceImpl(
                webSocketPushService,
                notificationWebhookClient,
                notificationProperties,
                weChatMiniProgramService
        );
    }

    @Test
    void notifySos_shouldPushWebsocketAndWebhookChannelsWhenEnabled() {
        notificationProperties.getSms().setEnabled(true);
        notificationProperties.getSms().setWebhookUrl("https://example.com/sms");
        notificationProperties.getWechatOfficial().setEnabled(true);
        notificationProperties.getWechatOfficial().setWebhookUrl("https://example.com/wechat");

        User parent = buildUser(1L, "妈妈", null, null);
        User child = buildUser(2L, "女儿", "13800138000", "openid_child");
        Message message = new Message();
        message.setId(10L);
        message.setContent("妈妈发起了紧急求助，请尽快联系。");

        alertNotificationService.notifySos(parent, child, 99L, message);

        verify(webSocketPushService).pushSosAlert(eq(2L), anyMap());
        verify(notificationWebhookClient, times(2)).postJson(anyString(), anyMap());
    }

    @Test
    void notifySos_shouldSkipExternalChannelsWhenReceiverMissingTargets() {
        notificationProperties.getSms().setEnabled(true);
        notificationProperties.getSms().setWebhookUrl("https://example.com/sms");
        notificationProperties.getWechatOfficial().setEnabled(true);
        notificationProperties.getWechatOfficial().setWebhookUrl("https://example.com/wechat");

        User parent = buildUser(1L, "妈妈", null, null);
        User child = buildUser(2L, "儿子", "", "");
        Message message = new Message();
        message.setId(11L);
        message.setContent("SOS");

        alertNotificationService.notifySos(parent, child, 100L, message);

        verify(webSocketPushService).pushSosAlert(eq(2L), anyMap());
        verify(notificationWebhookClient, never()).postJson(anyString(), anyMap());
    }

    @Test
    void notifySos_shouldFallbackToSimulationPayloadWhenWebhookMissing() {
        notificationProperties.getSms().setEnabled(true);
        notificationProperties.getWechatOfficial().setEnabled(true);

        User parent = buildUser(1L, "妈妈", null, null);
        User child = buildUser(2L, "儿子", "13800138000", "openid_child");
        Message message = new Message();
        message.setId(12L);
        message.setContent("SOS");

        alertNotificationService.notifySos(parent, child, 101L, message);

        verify(notificationWebhookClient, never()).postJson(anyString(), anyMap());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPushService).pushSosAlert(eq(2L), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(12L, payload.get("messageId"));
        assertEquals(101L, payload.get("familyBindId"));
        assertTrue(String.valueOf(payload.get("content")).contains("SOS"));
    }

    private User buildUser(Long id, String nickname, String phone, String openid) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setOpenid(openid);
        return user;
    }
}
