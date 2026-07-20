package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.HealthProfile;
import com.heartlink.entity.Product;
import com.heartlink.entity.Review;
import com.heartlink.mapper.ProductMapper;
import com.heartlink.service.AiService;
import com.heartlink.service.HealthProfileService;
import com.heartlink.service.ProductService;
import com.heartlink.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final HealthProfileService healthProfileService;
    private final AiService aiService;
    private final ReviewService reviewService;

    @Override
    public IPage<Product> pageProducts(Integer page, Integer size, Long categoryId, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(Product::getDeleted, 0);

        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }

        wrapper.orderByDesc(Product::getSales);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Product getProductDetail(Long productId) {
        Product product = getById(productId);
        if (product == null) {
            throw new RuntimeException("product not found");
        }
        return enrichProductDetail(product);
    }

    @Override
    public Map<String, Object> getProductDetailWithAI(Long productId, Long parentId) {
        Product product = getProductDetail(productId);

        Map<String, Object> result = new HashMap<>();
        result.put("product", product);

        if (parentId != null) {
            Map<String, Object> healthAnalysis = analyzeHealthRisk(productId, parentId);
            result.put("healthAnalysis", healthAnalysis);
        }

        return result;
    }

    @Override
    public List<Product> getRecommendProducts(Long parentId, Integer limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(Product::getDeleted, 0)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + limit);

        return list(wrapper);
    }

    @Override
    public Map<String, Object> analyzeHealthRisk(Long productId, Long parentId) {
        Product product = getById(productId);
        HealthProfile profile = healthProfileService.getOrCreateByParentId(parentId);
        return aiService.analyzeProductHealth(product, profile);
    }

    private Product enrichProductDetail(Product product) {
        product.setReviewsSummary(null);
        IPage<Review> reviewPage = reviewService.getProductReviews(product.getId(), 1, 6);
        product.setReviewPreview(reviewPage != null ? reviewPage.getRecords() : Collections.emptyList());
        return product;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object searchByImage(String imageBase64) {
        Object result = aiService.searchByImage(imageBase64);
        if (result instanceof Map) {
            Map<String, Object> aiResult = (Map<String, Object>) result;
            List<?> keywords = (List<?>) aiResult.get("keywords");
            if (keywords != null && !keywords.isEmpty()) {
                String keyword = keywords.get(0).toString();
                IPage<Product> products = pageProducts(1, 10, null, keyword);
                aiResult.put("products", products.getRecords());
            }
        }
        return result;
    }

    @Override
    public void adjustInventoryAndSales(Long productId, int stockDelta, int salesDelta) {
        if (productId == null || (stockDelta == 0 && salesDelta == 0)) {
            return;
        }

        boolean updated = lambdaUpdate()
                .eq(Product::getId, productId)
                .setSql("stock = GREATEST(COALESCE(stock, 0) + (" + stockDelta + "), 0), " +
                        "sales = GREATEST(COALESCE(sales, 0) + (" + salesDelta + "), 0)")
                .update();
        if (!updated) {
            throw new RuntimeException("failed to update product stats");
        }
    }
}
