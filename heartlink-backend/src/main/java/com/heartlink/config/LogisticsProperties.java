package com.heartlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "logistics")
public class LogisticsProperties {

    public static final String MANUAL_PROVIDER = "MANUAL";
    public static final String MOCK_PROVIDER = "MOCK";

    private boolean enabled = false;
    private String provider = "kdniao";
    private boolean queryOnShip = true;
    private Kdniao kdniao = new Kdniao();

    public boolean isQueryEnabled() {
        if (!enabled) {
            return false;
        }
        if (isMockProvider()) {
            return true;
        }
        return isKdniaoProvider()
                && kdniao != null
                && kdniao.isEnabled()
                && StringUtils.hasText(kdniao.getEbusinessId())
                && StringUtils.hasText(kdniao.getAppKey())
                && StringUtils.hasText(kdniao.getBaseUrl());
    }

    public String resolveProviderCode() {
        if (!isQueryEnabled()) {
            return MANUAL_PROVIDER;
        }
        if (isMockProvider()) {
            return MOCK_PROVIDER;
        }
        return defaultString(provider, "kdniao").toUpperCase();
    }

    public boolean isMockProvider() {
        return "mock".equalsIgnoreCase(defaultString(provider, "kdniao"));
    }

    public boolean isKdniaoProvider() {
        return "kdniao".equalsIgnoreCase(defaultString(provider, "kdniao"));
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    @Data
    public static class Kdniao {
        private boolean enabled = false;
        private String ebusinessId;
        private String appKey;
        private String baseUrl = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
        private String queryRequestType = "8002";
    }
}
