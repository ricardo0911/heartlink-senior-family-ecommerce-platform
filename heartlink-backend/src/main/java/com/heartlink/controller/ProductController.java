package com.heartlink.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.entity.Product;
import com.heartlink.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品控制器
 */
@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    
    @Operation(summary = "分页查询商品")
    @GetMapping("/page")
    public Result<IPage<Product>> pageProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        IPage<Product> result = productService.pageProducts(page, size, categoryId, keyword);
        return Result.success(result);
    }
    
    @Operation(summary = "获取商品详情")
    @GetMapping("/{id}")
    public Result<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getProductDetail(id);
        return Result.success(product);
    }
    
    @Operation(summary = "获取商品详情(带AI健康分析)")
    @GetMapping("/{id}/with-analysis")
    public Result<Map<String, Object>> getProductWithAnalysis(
            @PathVariable Long id,
            @RequestParam(required = false) Long parentId) {
        Map<String, Object> result = productService.getProductDetailWithAI(id, parentId);
        return Result.success(result);
    }
    
    @Operation(summary = "获取推荐商品")
    @GetMapping("/recommend")
    public Result<List<Product>> getRecommend(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Product> products = productService.getRecommendProducts(parentId, limit);
        return Result.success(products);
    }
    
    @Operation(summary = "AI分析商品健康风险")
    @GetMapping("/{id}/health-risk")
    public Result<Map<String, Object>> analyzeHealthRisk(
            @PathVariable Long id,
            @RequestParam Long parentId) {
        Map<String, Object> result = productService.analyzeHealthRisk(id, parentId);
        return Result.success(result);
    }

    @Operation(summary = "以图搜物")
    @PostMapping("/search-by-image")
    public Result<Object> searchByImage(@RequestBody java.util.Map<String, String> body) {
        String imageBase64 = body.get("image");
        if (imageBase64 == null || imageBase64.isEmpty()) {
            return Result.error("请提供图片");
        }
        Object aiResult = productService.searchByImage(imageBase64);
        return Result.success(aiResult);
    }
}
