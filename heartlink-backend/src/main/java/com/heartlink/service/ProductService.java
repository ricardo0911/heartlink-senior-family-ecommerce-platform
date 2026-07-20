package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.entity.Product;

import java.util.List;
import java.util.Map;

/**
 * 商品服务接口
 */
public interface ProductService extends IService<Product> {
    
    /**
     * 分页查询商品
     */
    IPage<Product> pageProducts(Integer page, Integer size, Long categoryId, String keyword);

    Product getProductDetail(Long productId);

    /**
     * 获取商品详情(带AI分析)
     */
    Map<String, Object> getProductDetailWithAI(Long productId, Long parentId);
    
    /**
     * 获取推荐商品
     */
    List<Product> getRecommendProducts(Long parentId, Integer limit);
    
    /**
     * AI分析商品健康风险
     */
    Map<String, Object> analyzeHealthRisk(Long productId, Long parentId);

    /**
     * 以图搜物
     */
    Object searchByImage(String imageBase64);

    void adjustInventoryAndSales(Long productId, int stockDelta, int salesDelta);
}
