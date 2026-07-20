package com.heartlink.service;

import java.util.Map;

public interface NotificationWebhookClient {

    void postJson(String url, Map<String, Object> payload);
}
