package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.SmartShelf;

import java.util.List;
import java.util.Map;

/**
 * 智能货架服务接口
 */
public interface SmartShelfService extends IService<SmartShelf> {
    
    /**
     * 推送商品到货架(子女端)
     */
    SmartShelf pushToShelf(Long childId, Long parentId, Long productId, String voiceMessage);
    
    /**
     * 批量推送商品(子女端)
     */
    List<SmartShelf> batchPushToShelf(Long childId, Long parentId, List<Long> productIds, String voiceMessage);
    
    /**
     * 获取货架商品(长辈端)
     */
    IPage<Map<String, Object>> getShelfItems(Long parentId, Integer page, Integer size);
    
    /**
     * 长辈反馈(喜欢/不喜欢)
     */
    SmartShelf react(Long shelfId, Long parentId, String reaction);

    SmartShelf collect(Long parentId, Long productId);
    
    /**
     * 获取喜欢的商品(子女端查看)
     */
    List<SmartShelf> getLikedItems(Long parentId);

    /**
     * 获取子女推送记录(子女端查看，含商品信息)
     */
    IPage<Map<String, Object>> getChildShelfItems(Long childId, Long parentId, String reaction, Integer page, Integer size);
}
