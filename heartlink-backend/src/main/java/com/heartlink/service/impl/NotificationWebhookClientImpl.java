package com.heartlink.service.impl;

import com.alibaba.fastjson2.JSON;
import com.heartlink.service.NotificationWebhookClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class NotificationWebhookClientImpl implements NotificationWebhookClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public void postJson(String url, Map<String, Object> payload) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("notification webhook failed: HTTP " + response.code());
            }
        } catch (IOException e) {
            log.error("notification webhook request failed, url={}", url, e);
            throw new RuntimeException("notification webhook request failed", e);
        }
    }
}
