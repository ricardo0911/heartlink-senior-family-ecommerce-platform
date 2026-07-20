package com.heartlink.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heartlink.entity.HealthProfile;
import com.heartlink.entity.Product;
import com.heartlink.service.AiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI服务实现 - 调用Python AI微服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {
    
    @Value("${ai-service.base-url:http://localhost:8090}")
    private String aiServiceUrl;
    
    @Value("${ai-service.timeout:30000}")
    private int timeout;
    
    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initHttpClient() {
        long timeoutMs = Math.max(timeout, 1000);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }
    
    @Override
    public Map<String, Object> analyzeProductHealth(Product product, HealthProfile profile) {
        Map<String, Object> result = new HashMap<>();
        Set<String> warnings = new LinkedHashSet<>();
        Set<String> suggestions = new LinkedHashSet<>();
        String productText = buildProductText(product);
        
        // 本地规则分析（备用，当AI服务不可用时）
        if (profile.getChronicDiseases() != null) {
            for (String disease : profile.getChronicDiseases()) {
                applyDiseaseRule(disease, productText, warnings, suggestions);
            }
        }

        if (profile.getAllergies() != null) {
            for (String allergen : profile.getAllergies()) {
                if (allergen != null && !allergen.isBlank() && productText.contains(allergen)) {
                    warnings.add("⚠️ 长辈对" + allergen + "过敏，请仔细查看成分");
                    suggestions.add("确认商品不含" + allergen + "成分");
                }
            }
        }
        
        // 尝试调用AI服务
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("product", product);
            request.put("profile", profile);
            
            String response = callAiService("/api/analyze/health", request);
            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                appendTextArray(json.get("warnings"), warnings);
                appendTextArray(json.get("suggestions"), suggestions);
                String aiAnalysis = readText(json.get("aiAnalysis"));
                if (aiAnalysis != null) {
                    result.put("aiAnalysis", aiAnalysis);
                }
            }
        } catch (Exception e) {
            log.warn("AI服务调用失败，使用本地规则: {}", e.getMessage());
        }
        
        result.put("warnings", new ArrayList<>(warnings));
        result.put("suggestions", new ArrayList<>(suggestions));
        result.put("safe", warnings.isEmpty());
        
        return result;
    }
    
    @Override
    public String summarizeReviews(String reviews) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("reviews", reviews);
            
            String response = callAiService("/api/summarize/reviews", request);
            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                String summary = readText(json.get("summary"));
                if (summary != null) {
                    return summary;
                }
            }
        } catch (Exception e) {
            log.warn("评论总结失败: {}", e.getMessage());
        }
        return "暂无评论总结";
    }
    
    @Override
    public String generateVoiceText(Product product) {
        // 生成适老化的语音描述
        StringBuilder sb = new StringBuilder();
        sb.append("这款").append(product.getName());
        
        if (product.getHealthTags() != null && !product.getHealthTags().isEmpty()) {
            sb.append("，");
            sb.append(String.join("、", product.getHealthTags()));
        }
        
        sb.append("，售价").append(product.getPrice()).append("元。");
        
        if (product.getReviewsSummary() != null) {
            sb.append("买过的人评价：").append(product.getReviewsSummary());
        }
        
        return sb.toString();
    }
    
    @Override
    public String generateGreetingCard(String productName, String relation, String occasion) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("productName", productName);
            request.put("relation", relation);
            request.put("occasion", occasion);
            
            String response = callAiService("/api/generate/greeting", request);
            if (response != null) {
                JsonNode json = objectMapper.readTree(response);
                String greeting = readText(json.get("greeting"));
                if (greeting != null) {
                    return greeting;
                }
            }
        } catch (Exception e) {
            log.warn("贺卡生成失败: {}", e.getMessage());
        }
        
        // 默认贺卡
        return String.format("亲爱的%s：\n\n这份%s是我精心为您挑选的，愿它能给您带来温暖和快乐。\n\n爱您的孩子", 
                relation != null ? relation : "妈妈", productName);
    }
    
    @Override
    public Object searchByImage(String imageBase64) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("image", imageBase64);
            
            String response = callAiService("/api/search/image", request);
            if (response != null) {
                return objectMapper.readValue(response, Object.class);
            }
        } catch (Exception e) {
            log.warn("以图搜图失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
    
    @Override
    public byte[] textToSpeech(String text) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("text", text);
            
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(request),
                    MediaType.parse("application/json")
            );
            
            Request httpRequest = new Request.Builder()
                    .url(aiServiceUrl + "/api/tts")
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
            }
        } catch (Exception e) {
            log.warn("TTS失败: {}", e.getMessage());
        }
        return null;
    }

    private void applyDiseaseRule(String disease, String productText, Set<String> warnings, Set<String> suggestions) {
        if ("糖尿病".equals(disease) || "高血糖".equals(disease)) {
            if (containsAny(productText, "糖", "甜", "蔗糖", "含糖", "高糖")) {
                warnings.add("⚠️ 长辈血糖偏高，该商品含糖量可能较高");
                suggestions.add("建议选择无糖或低糖替代品");
            }
            return;
        }

        if ("高血压".equals(disease)) {
            if (containsAny(productText, "盐", "咸", "高盐", "腌制", "钠")) {
                warnings.add("⚠️ 长辈有高血压，该商品可能含盐量较高");
                suggestions.add("建议选择低钠食品");
            }
            return;
        }

        if ("高血脂".equals(disease)) {
            if (containsAny(productText, "高脂", "油炸", "奶油", "肥肉", "胆固醇")) {
                warnings.add("⚠️ 长辈有高血脂，该商品脂肪或胆固醇含量可能偏高");
                suggestions.add("建议优先选择低脂、清淡类商品");
            }
            return;
        }

        if ("骨质疏松".equals(disease)) {
            if (containsAny(productText, "高钠", "碳酸", "咖啡因")) {
                warnings.add("⚠️ 长辈有骨质疏松风险，该商品可能不利于钙质吸收");
                suggestions.add("建议搭配高钙、低钠类商品");
            }
            return;
        }

        if ("痛风".equals(disease) || "高尿酸".equals(disease)) {
            if (containsAny(productText, "海鲜", "内脏", "啤酒", "高嘌呤")) {
                warnings.add("⚠️ 长辈有痛风或尿酸偏高情况，该商品可能含嘌呤较高");
                suggestions.add("建议避免高嘌呤食物，选择清淡饮食");
            }
            return;
        }

        if ("老寒腿".equals(disease)) {
            if (containsAny(productText, "凉", "冰", "寒")) {
                warnings.add("⚠️ 长辈有畏寒或关节不适情况，使用时需注意保暖");
                suggestions.add("建议优先选择保暖、护膝或热敷类商品");
            }
        }
    }

    private String buildProductText(Product product) {
        List<String> parts = new ArrayList<>();
        if (product.getName() != null) {
            parts.add(product.getName());
        }
        if (product.getDescription() != null) {
            parts.add(product.getDescription());
        }
        if (product.getWarningTags() != null && !product.getWarningTags().isEmpty()) {
            parts.add(String.join(" ", product.getWarningTags()));
        }
        if (product.getHealthTags() != null && !product.getHealthTags().isEmpty()) {
            parts.add(String.join(" ", product.getHealthTags()));
        }
        return String.join(" ", parts);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private String callAiService(String path, Map<String, Object> data) throws IOException {
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(data),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(aiServiceUrl + path)
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    private void appendTextArray(JsonNode node, Set<String> target) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String value = readText(item);
            if (value != null) {
                target.add(value);
            }
        }
    }

    private String readText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? null : text;
    }
}
