package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.entity.Review;
import com.heartlink.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-评价管理")
@RestController
@RequestMapping("/api/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "分页查询评价")
    @GetMapping("/page")
    public Result<IPage<Review>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long productId) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) wrapper.eq(Review::getProductId, productId);
        wrapper.orderByDesc(Review::getCreatedAt);
        IPage<Review> result = reviewService.page(new Page<>(page, size), wrapper);
        return Result.success(result);
    }

    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewService.removeById(id);
        return Result.success(null);
    }
}
