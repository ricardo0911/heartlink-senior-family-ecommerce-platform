package com.heartlink.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.heartlink.config.WeChatMiniProgramProperties;
import com.heartlink.entity.Message;
import com.heartlink.entity.User;
import com.heartlink.service.WeChatMiniProgramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatMiniProgramServiceImpl implements WeChatMiniProgramService {

    private static final String CODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SUBSCRIBE_MESSAGE_SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final WeChatMiniProgramProperties properties;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    private volatile String accessToken;
    private volatile long accessTokenExpireAt;

    @Override
    public String resolveOpenid(String code) {
        validateBaseConfig();
        if (StrUtil.isBlank(code)) {
            throw new RuntimeException("wechat login code cannot be blank");
        }

        HttpUrl url = HttpUrl.parse(CODE_TO_SESSION_URL).newBuilder()
                .addQueryParameter("appid", properties.getAppId())
                .addQueryParameter("secret", properties.getAppSecret())
                .addQueryParameter("js_code", code)
                .addQueryParameter("grant_type", "authorization_code")
                .build();

        JSONObject body = executeGet(url);
        String openid = body.getString("openid");
        if (StrUtil.isBlank(openid)) {
            throw new RuntimeException("failed to resolve wechat openid: " + body);
        }
        return openid;
    }

    @Override
    public void sendSosSubscribeMessage(User parent, User child, Message message) {
        if (!properties.isEnabled() || !properties.getSubscribe().isEnabled()) {
            return;
        }
        validateBaseConfig();
        if (child == null || StrUtil.isBlank(child.getOpenid())) {
            log.warn("skip mini program subscribe message because child openid is blank, childId={}", child != null ? child.getId() : null);
            return;
        }
        String templateId = properties.getSubscribe().getSosTemplateId();
        if (StrUtil.isBlank(templateId)) {
            log.warn("skip mini program subscribe message because sos template id is blank");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("touser", child.getOpenid());
        payload.put("template_id", templateId);
        payload.put("page", properties.getSubscribe().getPage());
        payload.put("miniprogram_state", properties.getSubscribe().getMiniprogramState());
        payload.put("lang", properties.getSubscribe().getLang());
        payload.put("data", buildSosData(parent, message));

        String token = getAccessToken();
        HttpUrl url = HttpUrl.parse(SUBSCRIBE_MESSAGE_SEND_URL).newBuilder()
                .addQueryParameter("access_token", token)
                .build();

        JSONObject response = executePost(url, payload);
        Integer errCode = response.getInteger("errcode");
        if (errCode != null && errCode != 0) {
            throw new RuntimeException("wechat subscribe message send failed: " + response);
        }
    }

    private Map<String, Object> buildSosData(User parent, Message message) {
        String parentName = parent != null && StrUtil.isNotBlank(parent.getNickname()) ? parent.getNickname() : "家人";
        String content = message != null && StrUtil.isNotBlank(message.getContent())
                ? message.getContent()
                : parentName + "发起了紧急求助，请尽快联系。";

        Map<String, Object> data = new HashMap<>();
        data.put(properties.getSubscribe().getAlertTypeKey(), valueNode("紧急告警"));
        data.put(properties.getSubscribe().getContentKey(), valueNode(limitText(content, 20)));
        data.put(properties.getSubscribe().getTimeKey(), valueNode(LocalDateTime.now().withNano(0).toString().replace('T', ' ')));
        return data;
    }

    private Map<String, String> valueNode(String value) {
        Map<String, String> node = new HashMap<>();
        node.put("value", value);
        return node;
    }

    private String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StrUtil.isNotBlank(accessToken) && now < accessTokenExpireAt) {
            return accessToken;
        }

        synchronized (this) {
            long current = System.currentTimeMillis();
            if (StrUtil.isNotBlank(accessToken) && current < accessTokenExpireAt) {
                return accessToken;
            }

            HttpUrl url = HttpUrl.parse(ACCESS_TOKEN_URL).newBuilder()
                    .addQueryParameter("grant_type", "client_credential")
                    .addQueryParameter("appid", properties.getAppId())
                    .addQueryParameter("secret", properties.getAppSecret())
                    .build();

            JSONObject body = executeGet(url);
            String newToken = body.getString("access_token");
            Integer expiresIn = body.getInteger("expires_in");
            if (StrUtil.isBlank(newToken) || expiresIn == null) {
                throw new RuntimeException("failed to get mini program access token: " + body);
            }
            accessToken = newToken;
            accessTokenExpireAt = current + Math.max(300, expiresIn - 300) * 1000L;
            return accessToken;
        }
    }

    private JSONObject executeGet(HttpUrl url) {
        Request request = new Request.Builder().url(url).get().build();
        return execute(request);
    }

    private JSONObject executePost(HttpUrl url, Object payload) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(payload), JSON_MEDIA_TYPE))
                .build();
        return execute(request);
    }

    private JSONObject execute(Request request) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("wechat api request failed: HTTP " + response.code());
            }
            return JSON.parseObject(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException("wechat api request failed", e);
        }
    }

    private void validateBaseConfig() {
        if (!properties.isEnabled()) {
            throw new RuntimeException("mini program capability is disabled");
        }
        if (StrUtil.isBlank(properties.getAppId()) || StrUtil.isBlank(properties.getAppSecret())) {
            throw new RuntimeException("mini program appId/appSecret is not configured");
        }
    }

    private String limitText(String text, int maxLength) {
        String value = String.valueOf(text == null ? "" : text).trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
