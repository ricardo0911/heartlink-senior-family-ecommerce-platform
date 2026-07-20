package com.heartlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.mini-program")
public class WeChatMiniProgramProperties {

    private boolean enabled = false;
    private String appId;
    private String appSecret;
    private SubscribeMessage subscribe = new SubscribeMessage();

    @Data
    public static class SubscribeMessage {
        private boolean enabled = false;
        private String sosTemplateId;
        private String page = "pages/index/index";
        private String miniprogramState = "formal";
        private String lang = "zh_CN";
        private String alertTypeKey = "thing1";
        private String contentKey = "thing2";
        private String timeKey = "time3";
    }
}
