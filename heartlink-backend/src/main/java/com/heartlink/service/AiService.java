package com.heartlink.service;

import com.heartlink.entity.HealthProfile;
import com.heartlink.entity.Product;

import java.util.Map;

/**
 * AI服务接口 - 调用Python AI微服务
 */
public interface AiService {
    
    /**
     * 分析商品健康风险
     */
    Map<String, Object> analyzeProductHealth(Product product, HealthProfile profile);
    
    /**
     * 总结商品评论
     */
    String summarizeReviews(String reviews);
    
    /**
     * 生成语音播报文本
     */
    String generateVoiceText(Product product);
    
    /**
     * 生成情感贺卡
     */
    String generateGreetingCard(String productName, String relation, String occasion);
    
    /**
     * 以图搜图
     */
    Object searchByImage(String imageBase64);
    
    /**
     * 生成语音(TTS)
     */
    byte[] textToSpeech(String text);
}
