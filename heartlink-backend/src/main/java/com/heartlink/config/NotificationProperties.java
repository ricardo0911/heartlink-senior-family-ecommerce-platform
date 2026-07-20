package com.heartlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Channel websocket = new Channel(true);
    private WebhookChannel sms = new WebhookChannel();
    private WechatOfficialChannel wechatOfficial = new WechatOfficialChannel();

    @Data
    public static class Channel {
        private boolean enabled = true;

        public Channel() {
        }

        public Channel(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Data
    public static class WebhookChannel {
        private boolean enabled = false;
        private String webhookUrl;
        private String signName;
        private String templateCode;
    }

    @Data
    public static class WechatOfficialChannel extends WebhookChannel {
        private String appId;
        private String templateId;
    }
}
