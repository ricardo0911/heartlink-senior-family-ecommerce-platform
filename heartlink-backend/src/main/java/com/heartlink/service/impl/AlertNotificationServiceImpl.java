package com.heartlink.service.impl;

import cn.hutool.core.util.StrUtil;
import com.heartlink.config.NotificationProperties;
import com.heartlink.entity.Message;
import com.heartlink.entity.User;
import com.heartlink.service.AlertNotificationService;
import com.heartlink.service.NotificationWebhookClient;
import com.heartlink.service.WeChatMiniProgramService;
import com.heartlink.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationServiceImpl implements AlertNotificationService {

    private final WebSocketPushService webSocketPushService;
    private final NotificationWebhookClient notificationWebhookClient;
    private final NotificationProperties notificationProperties;
    private final WeChatMiniProgramService weChatMiniProgramService;

    @Override
    public void notifySos(User parent, User child, Long familyBindId, Message message) {
        Map<String, Object> payload = buildSosPayload(parent, child, familyBindId, message);

        if (notificationProperties.getWebsocket().isEnabled()) {
            webSocketPushService.pushSosAlert(child.getId(), payload);
        }

        sendMiniProgramIfNeeded(parent, child, message);
        sendSmsIfNeeded(parent, child, payload);
        sendWechatOfficialIfNeeded(parent, child, payload);
    }

    private void sendMiniProgramIfNeeded(User parent, User child, Message message) {
        try {
            weChatMiniProgramService.sendSosSubscribeMessage(parent, child, message);
        } catch (RuntimeException e) {
            log.warn("mini program subscribe message send failed, childId={}", child != null ? child.getId() : null, e);
        }
    }

    private Map<String, Object> buildSosPayload(User parent, User child, Long familyBindId, Message message) {
        String parentName = parent != null && StrUtil.isNotBlank(parent.getNickname()) ? parent.getNickname() : "家人";
        String content = message != null && StrUtil.isNotBlank(message.getContent())
                ? message.getContent()
                : parentName + "发起了紧急求助，请尽快联系。";

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", message != null ? message.getId() : null);
        payload.put("familyBindId", familyBindId);
        payload.put("parentId", parent != null ? parent.getId() : null);
        payload.put("parentName", parentName);
        payload.put("parentAvatar", parent != null ? parent.getAvatar() : null);
        payload.put("childId", child != null ? child.getId() : null);
        payload.put("childName", child != null ? child.getNickname() : null);
        payload.put("content", content);
        payload.put("alertType", "SOS");
        return payload;
    }

    private void sendSmsIfNeeded(User parent, User child, Map<String, Object> payload) {
        NotificationProperties.WebhookChannel sms = notificationProperties.getSms();
        if (!sms.isEnabled()) {
            return;
        }
        if (child == null || StrUtil.isBlank(child.getPhone())) {
            log.warn("skip sms notification because receiver phone is blank, childId={}", child != null ? child.getId() : null);
            return;
        }

        Map<String, Object> smsPayload = new HashMap<>(payload);
        smsPayload.put("channel", "SMS");
        smsPayload.put("receiverPhone", child.getPhone());
        smsPayload.put("signName", sms.getSignName());
        smsPayload.put("templateCode", sms.getTemplateCode());
        smsPayload.put("receiverName", child.getNickname());
        smsPayload.put("senderName", parent != null ? parent.getNickname() : null);

        sendWebhookOrSimulate("SMS", sms.getWebhookUrl(), smsPayload);
    }

    private void sendWechatOfficialIfNeeded(User parent, User child, Map<String, Object> payload) {
        NotificationProperties.WechatOfficialChannel wechatOfficial = notificationProperties.getWechatOfficial();
        if (!wechatOfficial.isEnabled()) {
            return;
        }
        if (child == null || StrUtil.isBlank(child.getOpenid())) {
            log.warn("skip wechat official notification because receiver openid is blank, childId={}", child != null ? child.getId() : null);
            return;
        }

        Map<String, Object> wechatPayload = new HashMap<>(payload);
        wechatPayload.put("channel", "WECHAT_OFFICIAL");
        wechatPayload.put("receiverOpenid", child.getOpenid());
        wechatPayload.put("appId", wechatOfficial.getAppId());
        wechatPayload.put("templateId", wechatOfficial.getTemplateId());
        wechatPayload.put("receiverName", child.getNickname());
        wechatPayload.put("senderName", parent != null ? parent.getNickname() : null);

        sendWebhookOrSimulate("WECHAT_OFFICIAL", wechatOfficial.getWebhookUrl(), wechatPayload);
    }

    private void sendWebhookOrSimulate(String channel, String webhookUrl, Map<String, Object> payload) {
        if (StrUtil.isBlank(webhookUrl)) {
            log.info("{} notification simulated: {}", channel, payload);
            return;
        }
        notificationWebhookClient.postJson(webhookUrl, payload);
    }
}
